package com.neonclash.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.neonclash.NeonClash;
import com.neonclash.compat.TaczBridge;
import com.neonclash.config.NCConfig;
import com.neonclash.core.round.RoundEngine;
import com.neonclash.core.upgrade.UpgradeDef;
import com.neonclash.network.C2SActionPacket;
import com.neonclash.network.NCNetwork;
import com.neonclash.registry.NCItems;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.sound.GunSoundInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 録画用のデモ操作（-Dneonclash.demo=true の時だけ動く）。
 * ワールドを自動で作り、コマンドでアリーナを組んで Husk 相手の練習試合を始め、
 * 照準・射撃・ブリンク・剣・パリィ・強化選択を自動で行ってからゲームを終了する。
 * 画面は Screenshot.takeScreenshot で run/demo_frames/ に連番 PNG で保存し、終わったら run/demo_done を作る。
 * fps は -Dneonclash.demoFps（既定 10）。10 を超えるとウィンドウを 720p にする。
 * 録画開始時のビートの位相は run/demo_beat.json、鳴った音は run/demo_sounds.jsonl に書く
 * （BGM を拍に合わせるためと、tools/sfx/mix_demo.py で動画に音を付けるため）。
 * 動画を見栄え良くするため、録画中だけ FOV を広げ、Husk を速くして銃を持たせる（試合のルールは変えない）。
 */
@Mod.EventBusSubscriber(modid = NeonClash.MODID, value = Dist.CLIENT)
public final class DemoDriver {
    private static final boolean ENABLED = Boolean.getBoolean("neonclash.demo");
    private static final String WORLD = "neonclash-demo";
    private static final int MAX_TICKS = 20 * 60 * 6;
    /** 録画は最大でこの長さ（実時間） */
    private static final long MAX_RECORD_MILLIS = 150_000;
    private static final int FPS = Integer.getInteger("neonclash.demoFps", 10);
    private static final int WIDE_FOV = 100;
    /** PNG の書き出しが追いつかないときに、未処理の画面をこれ以上溜めない（1 枚で数 MB ある） */
    private static final int MAX_PENDING_FRAMES = 60;

    private static final ExecutorService FRAME_WRITER = Executors.newFixedThreadPool(FPS > 10 ? 4 : 1);
    private static final AtomicInteger PENDING_FRAMES = new AtomicInteger();
    private static final List<String> SOUND_LOG = new ArrayList<>();
    private static long recordStart;
    private static int frameNumber;
    private static boolean capturing;

    private record Step(String name, int delay, Predicate<Minecraft> ready, Consumer<Minecraft> action) {
    }

    private static final List<Step> STEPS = new ArrayList<>();
    private static int stage;
    private static int stageTicks;
    private static int stepIndex;
    private static int stepTicks;
    private static int totalTicks;
    private static int fightTicks;
    private static int screenTicks;
    private static boolean fighting;

    // 自動操作の状態
    private static final Random RNG = new Random(20261031L);
    private static float moveForward;
    private static float moveStrafe;
    private static boolean moveJump;
    private static int strafeDir = 1;
    private static int nextStrafeFlip;
    private static int nextBlink = 40;
    /** 0 より大きければ、視線をずらしてから次の tick にブリンクする */
    private static float blinkAimOffset;
    private static int nextSword;
    private static int swordTicks;
    private static boolean usingBlade;
    private static long lastShotSlot = Long.MIN_VALUE;

    static {
        buildScript();
    }

    private DemoDriver() {
    }

    private static void buildScript() {
        step("setup", 60, mc -> true, mc -> {
            command(mc, "gamerule sendCommandFeedback false");
            command(mc, "gamerule doDaylightCycle false");
            command(mc, "gamerule doWeatherCycle false");
            command(mc, "gamerule doMobSpawning false");
            command(mc, "gamerule showDeathMessages false");
            command(mc, "difficulty normal");
            command(mc, "time set 13200");
            command(mc, "weather clear");
            command(mc, "tp @s ~ 140 ~");
        });
        step("arena floor", 20, mc -> true, mc -> {
            // 暗いインダストリアルな床と、チームカラーのライン
            command(mc, "fill ~-24 139 ~-24 ~24 139 ~24 minecraft:black_concrete");
            command(mc, "fill ~-24 140 ~-24 ~24 146 ~24 minecraft:air");
            command(mc, "fill ~-24 139 ~0 ~24 139 ~0 minecraft:sea_lantern");
            command(mc, "fill ~0 139 ~-24 ~0 139 ~24 minecraft:sea_lantern");
            command(mc, "fill ~-15 139 ~-4 ~-9 139 ~4 minecraft:red_concrete");
            command(mc, "fill ~9 139 ~-4 ~15 139 ~4 minecraft:light_blue_concrete");
            command(mc, "fill ~-24 139 ~-24 ~24 139 ~-24 minecraft:crying_obsidian");
            command(mc, "fill ~-24 139 ~24 ~24 139 ~24 minecraft:crying_obsidian");
        });
        step("arena cover", 20, mc -> true, mc -> {
            // 遮蔽物（ブリンクで回り込める）
            command(mc, "fill ~-5 140 ~-8 ~-4 143 ~-6 minecraft:gray_concrete");
            command(mc, "fill ~4 140 ~6 ~5 143 ~8 minecraft:gray_concrete");
            command(mc, "fill ~-5 144 ~-8 ~-4 144 ~-6 minecraft:red_stained_glass");
            command(mc, "fill ~4 144 ~6 ~5 144 ~8 minecraft:light_blue_stained_glass");
            command(mc, "fill ~-1 140 ~10 ~1 141 ~11 minecraft:polished_deepslate");
            command(mc, "fill ~-1 140 ~-11 ~1 141 ~-10 minecraft:polished_deepslate");
            // 外周の柵（Husk が落ちないように）
            command(mc, "fill ~-24 140 ~-24 ~24 141 ~-24 minecraft:gray_stained_glass");
            command(mc, "fill ~-24 140 ~24 ~24 141 ~24 minecraft:gray_stained_glass");
            command(mc, "fill ~-24 140 ~-24 ~-24 141 ~24 minecraft:gray_stained_glass");
            command(mc, "fill ~24 140 ~-24 ~24 141 ~24 minecraft:gray_stained_glass");
            command(mc, "tp @s ~ 140 ~");
        });
        step("arena set", 20, mc -> true, mc -> command(mc, "pvp arena set"));
        step("title", 20, mc -> true, mc -> {
            capturing = true;
            mc.gui.getChat().clearMessages(false);
            command(mc, "title @s times 5 50 10");
            command(mc, "title @s subtitle \"Neon Clash  /  demo (Forge 1.20.1 + TaCZ)\"");
            command(mc, "title @s title \"NEON CLASH\"");
        });
        step("practice", 60, mc -> true, mc -> {
            mc.gui.getChat().clearMessages(false);
            command(mc, "pvp practice 3");
            fighting = true;
        });
        step("finish", 0, DemoDriver::shouldFinish, DemoDriver::finish);
    }

    private static void step(String name, int delay, Predicate<Minecraft> ready, Consumer<Minecraft> action) {
        STEPS.add(new Step(name, delay, ready, action));
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        totalTicks++;
        if (totalTicks > MAX_TICKS && stage < 3) {
            NeonClash.LOGGER.error("[demo] timed out at stage {} step {}", stage, stepIndex);
            finish(mc);
            return;
        }
        switch (stage) {
            case 0 -> {
                // タイトル画面（またはその前の案内画面）が出たらワールドを作る
                if (FPS > 10 && stageTicks == 0) {
                    org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1280, 720);
                }
                if (mc.getOverlay() == null && mc.screen != null && ++stageTicks > 40) {
                    NeonClash.LOGGER.info("[demo] creating world from {}", mc.screen.getClass().getSimpleName());
                    createWorld(mc);
                    stage = 1;
                }
            }
            case 1 -> {
                if (mc.player != null && mc.level != null && mc.screen == null) {
                    stage = 2;
                    marker(mc, "demo_recording");
                    NeonClash.LOGGER.info("[demo] in world, starting script");
                }
            }
            case 2 -> {
                runScript(mc);
                if (stage == 2 && fighting) fight(mc);
                if (stage == 2 && capturing && totalTicks % 5 == 0) logMusic();
            }
            default -> {
            }
        }
    }

    /** 描画の直後に読み出す（tick ごとだと 20fps が上限になる）。 */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (ENABLED && event.phase == TickEvent.Phase.END && stage == 2 && capturing) {
            captureFrame(Minecraft.getInstance());
        }
    }

    /** 入力は移動のキーではなくここで書き換える（ウィンドウが後ろへ回っても動く）。ヒットストップより先に通す。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!ENABLED || !fighting) return;
        Input in = event.getInput();
        in.forwardImpulse = moveForward;
        in.leftImpulse = moveStrafe;
        in.up = moveForward > 0;
        in.down = moveForward < 0;
        in.left = moveStrafe > 0;
        in.right = moveStrafe < 0;
        in.jumping = moveJump;
    }

    /** 鳴った音を記録する。動画の音声は録音ではなく、この記録から組み立てる（録画環境に音声デバイスが無くても作れる）。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSound(PlaySoundEvent event) {
        if (!ENABLED || !capturing || recordStart == 0 || event.getSound() == null) return;
        Minecraft mc = Minecraft.getInstance();
        SoundInstance s = event.getSound();
        try {
            s.resolve(mc.getSoundManager()); // 音量とピッチは解決した後でないと読めない
            String name = s instanceof GunSoundInstance gun && gun.getRegistryName() != null ? gun.getRegistryName().toString() : s.getLocation().toString();
            float gain = Math.min(1f, s.getVolume());
            if (mc.player != null && !s.isRelative() && s.getAttenuation() != SoundInstance.Attenuation.NONE) {
                double d = Math.sqrt(mc.player.distanceToSqr(s.getX(), s.getY(), s.getZ()));
                gain *= (float) Math.max(0, 1 - d / (16.0 * Math.max(1f, s.getVolume())));
            }
            SOUND_LOG.add(String.format(Locale.ROOT, "{\"t\": %d, \"name\": \"%s\", \"gain\": %.3f, \"pitch\": %.3f, \"source\": \"%s\"}",
                    System.currentTimeMillis() - recordStart, name, gain, s.getPitch(), s.getSource().getName()));
        } catch (RuntimeException e) {
            NeonClash.LOGGER.warn("[demo] could not log sound {}", s.getLocation(), e);
        }
    }

    private static void logMusic() {
        if (recordStart == 0) return;
        float[] levels = MatchMusic.levels();
        SOUND_LOG.add(String.format(Locale.ROOT, "{\"t\": %d, \"music\": [%.3f, %.3f]}", System.currentTimeMillis() - recordStart, levels[0], levels[1]));
    }

    private static void runScript(Minecraft mc) {
        if (mc.player == null || stepIndex >= STEPS.size()) return;
        Step step = STEPS.get(stepIndex);
        if (++stepTicks < step.delay() || !step.ready().test(mc)) return;
        NeonClash.LOGGER.info("[demo] step {}", step.name());
        step.action().accept(mc);
        stepIndex++;
        stepTicks = 0;
    }

    /** 試合終了（勝敗の表示を少し見せてから）か、録画上限で終わり。 */
    private static boolean shouldFinish(Minecraft mc) {
        if (recordStart > 0 && System.currentTimeMillis() - recordStart > MAX_RECORD_MILLIS) return true;
        var hud = ClientState.hud;
        if (hud == null) return fightTicks > 200; // 試合が終わって HUD が消えた
        if (hud.phase() == RoundEngine.Phase.FINISHED.ordinal()) {
            finishedTicks++;
        }
        return finishedTicks > 60;
    }

    private static int finishedTicks;

    // ------------------------------------------------------------------ 自動操作

    private static void fight(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        fightTicks++;
        moveForward = moveStrafe = 0f;
        moveJump = false;

        // ラウンド間：強化 3 択を少し見せてから、一番レア度の高いものを選ぶ
        if (mc.screen instanceof UpgradeScreen) {
            if (++screenTicks == 45 && ClientState.offerChosen < 0 && !ClientState.offers.isEmpty()) {
                int best = 0;
                List<UpgradeDef> offers = ClientState.offers;
                for (int i = 1; i < offers.size(); i++) {
                    if (offers.get(i).rarity().ordinal() > offers.get(best).rarity().ordinal()) best = i;
                }
                NCNetwork.sendToServer(new C2SActionPacket(C2SActionPacket.Action.CHOOSE_UPGRADE, best));
            }
            return;
        }
        screenTicks = 0;
        if (p.isSpectator() || ClientState.hud == null || ClientState.hud.phase() != RoundEngine.Phase.ROUND.ordinal()) return;

        Husk target = null;
        double best = Double.MAX_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof Husk h && h.isAlive() && !h.isInvisible()) {
                double d = h.distanceToSqr(p);
                if (d < best) {
                    best = d;
                    target = h;
                }
            }
        }
        if (fightTicks % 60 == 0) {
            // 動画が見せ場なく終わらないよう、録画中だけ自分の被ダメージを減らす
            command(mc, "effect give @s minecraft:resistance 4 2 true");
            command(mc, "effect give @s minecraft:regeneration 4 1 true");
        }
        if (fightTicks % 20 == 0) botFire(mc);
        if (target == null) return;
        double dist = Math.sqrt(best);
        double dx = target.getX() - p.getX(), dz = target.getZ() - p.getZ();
        double dy = target.getY() + target.getBbHeight() * 0.6 - p.getEyeY();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        if (blinkAimOffset != 0f) {
            // 前の tick に視線をずらしてあるので、その向きへ飛ぶ（サーバーへ向きが届いた後に送る）
            send(C2SActionPacket.Action.BLINK);
            blinkAimOffset = 0f;
        }
        p.setYRot(Mth.rotLerp(0.55f, p.getYRot(), yaw));
        p.setXRot(Mth.lerp(0.55f, p.getXRot(), pitch));
        boolean aimed = Math.abs(Mth.wrapDegrees(p.getYRot() - yaw)) < 8f;
        p.setSprinting(true);
        moveForward = 1f;

        // 囲まれたら背後へ振り返ってブリンクで抜ける（相手の体で画面が埋まらないように）
        if (fightTicks >= nextBlink && dist < 3.5 && ClientState.hud.blinkCharges() > 0) {
            blinkAimOffset = 180f + (RNG.nextFloat() - 0.5f) * 60f;
            p.setYRot(yaw + blinkAimOffset);
            p.setXRot(-4f);
            nextBlink = fightTicks + 26 + RNG.nextInt(22);
            usingBlade = false;
            return;
        }
        // 持ち替えに時間がかかるので、行き来しないよう間を空ける
        if (dist < 3.0) usingBlade = true;
        else if (dist > 5 && swordTicks == 0) usingBlade = false;
        if (usingBlade) {
            // 寄られたら剣で迎え撃つ。近すぎたら下がる
            select(p, true);
            moveStrafe = strafeDir;
            if (dist < 2.2) {
                p.setSprinting(false);
                moveForward = -1f;
            }
            if (swordTicks > 0) swordTicks--;
            if (fightTicks >= nextSword) {
                send(C2SActionPacket.Action.SWORD);
                nextSword = fightTicks + 22;
                swordTicks = 10;
            } else if (fightTicks % 41 == 0) {
                send(C2SActionPacket.Action.PARRY);
            }
            return;
        }
        select(p, false);

        // 離れている：走りながら相手の周りを回る。時々切り返して跳ぶ
        if (fightTicks >= nextStrafeFlip) {
            strafeDir = -strafeDir;
            nextStrafeFlip = fightTicks + 18 + RNG.nextInt(24);
        }
        moveStrafe = strafeDir;
        if (dist < 6) {
            p.setSprinting(false);
            moveForward = -1f; // 撃ちながら下がる
        } else {
            moveForward = dist > 9 ? 1f : 0.3f;
        }
        moveJump = p.onGround() && RNG.nextInt(4) == 0;

        // ブリンク：視線を斜めにずらして、相手の横へ回り込むように飛ぶ
        if (fightTicks >= nextBlink && dist > 7 && dist < 18 && ClientState.hud.blinkCharges() > 0) {
            blinkAimOffset = (RNG.nextBoolean() ? 1 : -1) * (18f + RNG.nextFloat() * 14f);
            p.setYRot(yaw + blinkAimOffset);
            p.setXRot(-4f);
            nextBlink = fightTicks + 26 + RNG.nextInt(22);
            return;
        }

        // 射撃：拍と裏拍に合わせる
        double now = ClientState.serverNowMs() + 25; // 届くまでの遅れの分だけ早めに撃つ
        long beat = ClientState.BEAT.beatIndexAt(now);
        double phase = (now - ClientState.BEAT.beatTimeMs(beat)) / ClientState.BEAT.intervalMs();
        long slot = beat * 2 + (phase >= 0.5 ? 1 : 0);
        boolean onSlot = phase < 0.12 || (phase >= 0.5 && phase < 0.62);
        if (aimed && onSlot && slot != lastShotSlot) {
            lastShotSlot = slot;
            IClientPlayerGunOperator op = IClientPlayerGunOperator.fromLocalPlayer(p);
            ShootResult r = op.shoot();
            if (r == ShootResult.NO_AMMO) op.reload();
        }
    }

    /**
     * 録画の間だけ、Husk の 1 体がプレイヤーを狙って撃つ（青いビームと、自分が押される様子を映すため）。
     * 外れるよう少しずらす。統合サーバーのスレッドで動かす。
     */
    private static void botFire(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return;
        UUID id = mc.player.getUUID();
        float yawError = (RNG.nextFloat() - 0.5f) * 14f, pitchError = (RNG.nextFloat() - 0.5f) * 6f;
        int pick = RNG.nextInt(1 << 16);
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp == null || sp.isSpectator()) return;
            List<Husk> bots = sp.serverLevel().getEntitiesOfClass(Husk.class, sp.getBoundingBox().inflate(26), h -> h.isAlive() && !h.isInvisible());
            if (bots.isEmpty()) return;
            Husk bot = bots.get(pick % bots.size());
            IGunOperator op = IGunOperator.fromLivingEntity(bot);
            ItemStack held = bot.getMainHandItem();
            IGun gun = IGun.getIGunOrNull(held);
            if (gun == null) {
                bot.setItemInHand(InteractionHand.MAIN_HAND, TaczBridge.makeGun(NCConfig.SERVER.handgunId.get(), 17));
                op.initialData();
                op.draw(bot::getMainHandItem);
                return;
            }
            gun.setCurrentAmmoCount(held, 17);
            double dx = sp.getX() - bot.getX(), dz = sp.getZ() - bot.getZ();
            double dy = sp.getY() + sp.getBbHeight() * 0.6 - bot.getEyeY();
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f + yawError;
            float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG) + pitchError;
            op.shoot(() -> pitch, () -> yaw);
        });
    }

    private static void select(LocalPlayer p, boolean blade) {
        for (int i = 0; i < 9; i++) {
            var stack = p.getInventory().getItem(i);
            boolean match = blade ? stack.is(NCItems.NEON_BLADE.get()) : IGun.getIGunOrNull(stack) != null;
            if (match) {
                p.getInventory().selected = i;
                return;
            }
        }
    }

    private static void send(C2SActionPacket.Action action) {
        NCNetwork.sendToServer(new C2SActionPacket(action, 0));
    }

    // ------------------------------------------------------------------ ワールド・録画

    private static void createWorld(Minecraft mc) {
        mc.options.pauseOnLostFocus = false; // 録画中にウィンドウが後ろへ回っても試合を止めない
        if (FPS > 10) {
            mc.options.fov().set(WIDE_FOV); // 速さが伝わるように広げる（保存はされない）
        }
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.NORMAL, true,
                new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261030L, false, false),
                WorldPresets::createNormalWorldDimensions);
    }

    private static void command(Minecraft mc, String command) {
        mc.player.connection.sendCommand(command);
    }

    /**
     * 描画したフレームを読み出し、PNG 書き出しは別スレッドで行う。
     * 実時間で足りない分は同じ画面を複製するので、動画の速さは実際の見た目どおりになる（ソフトウェア描画で fps が低くても）。
     */
    private static void captureFrame(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (recordStart == 0) {
            recordStart = now;
            writeBeatInfo(mc);
        }
        int target = (int) ((now - recordStart) * FPS / 1000) + 1;
        int count = target - frameNumber;
        if (count <= 0 || PENDING_FRAMES.get() >= MAX_PENDING_FRAMES) return;
        NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget());
        Path dir = mc.gameDirectory.toPath().resolve("demo_frames");
        int first = frameNumber;
        frameNumber += count;
        PENDING_FRAMES.incrementAndGet();
        FRAME_WRITER.execute(() -> {
            try (image) {
                Files.createDirectories(dir);
                Path firstPath = dir.resolve(String.format("%05d.png", first));
                image.writeToFile(firstPath);
                for (int i = 1; i < count; i++) {
                    Files.copy(firstPath, dir.resolve(String.format("%05d.png", first + i)));
                }
            } catch (IOException e) {
                NeonClash.LOGGER.warn("[demo] could not write frame {}", first, e);
            } finally {
                PENDING_FRAMES.decrementAndGet();
            }
        });
    }

    /**
     * 録画開始時点のビートの位相を demo_beat.json に書く。BGM（ci/groove.py）はこれを読んで、
     * 拍番号 % 4 == 0（HUD のクリック音のアクセント拍）に小節の頭を合わせる。
     */
    private static void writeBeatInfo(Minecraft mc) {
        var beat = ClientState.BEAT;
        double serverNow = ClientState.serverNowMs();
        long next = beat.beatIndexAt(serverNow) + 1;
        double firstBeatMs = beat.beatTimeMs(next) - serverNow;
        String json = String.format(java.util.Locale.ROOT,
                "{\"enabled\": %s, \"bpm\": %.4f, \"first_beat_ms\": %.3f, \"first_beat_index\": %d}",
                beat.enabled(), beat.bpm(), firstBeatMs, next);
        try {
            Files.writeString(mc.gameDirectory.toPath().resolve("demo_beat.json"), json);
            NeonClash.LOGGER.info("[demo] beat info {}", json);
        } catch (IOException e) {
            NeonClash.LOGGER.warn("[demo] could not write beat info", e);
        }
    }

    private static void finish(Minecraft mc) {
        NeonClash.LOGGER.info("[demo] finished, {} frames", frameNumber);
        capturing = false;
        fighting = false;
        if (mc.player != null) command(mc, "pvp stop");
        try {
            Files.write(mc.gameDirectory.toPath().resolve("demo_sounds.jsonl"), SOUND_LOG);
        } catch (IOException e) {
            NeonClash.LOGGER.warn("[demo] could not write the sound log", e);
        }
        FRAME_WRITER.shutdown();
        try {
            FRAME_WRITER.awaitTermination(2, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        marker(mc, "demo_done");
        stage = 3;
        mc.stop();
    }

    private static void marker(Minecraft mc, String name) {
        try {
            Files.writeString(mc.gameDirectory.toPath().resolve(name), String.valueOf(System.currentTimeMillis()));
        } catch (IOException e) {
            NeonClash.LOGGER.warn("[demo] could not write marker {}", name, e);
        }
    }
}
