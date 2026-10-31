package com.neonclash.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.neonclash.NeonClash;
import com.neonclash.config.NCConfig;
import com.neonclash.game.SwordService;
import com.neonclash.network.C2SActionPacket;
import com.neonclash.network.NCNetwork;
import com.neonclash.registry.NCSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 入力・演出（カメラの揺れ、スローの FOV / 効果音ピッチ / 彩度）。 */
@Mod.EventBusSubscriber(modid = NeonClash.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {
    }

    /** 前の tick で攻撃キーが押されていたか（押しっぱなしの連続発火を除くため） */
    private static boolean attackHeldLastTick;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        attackHeldLastTick = mc.options.keyAttack.isDown();
        if (mc.player == null) return;
        ClientState.tick();
        BeamRenderer.tick();
        while (NCKeys.BLINK.consumeClick()) {
            NCNetwork.sendToServer(new C2SActionPacket(C2SActionPacket.Action.BLINK, 0));
        }
        while (NCKeys.PARRY.consumeClick()) {
            NCNetwork.sendToServer(new C2SActionPacket(C2SActionPacket.Action.PARRY, 0));
        }
        if (ClientState.hitStopTicks > 0) {
            // ヒットストップ：被弾側の移動を止める
            LocalPlayer p = mc.player;
            double keep = NCConfig.CLIENT.hitStopInputFreeze.get();
            Vec3 v = p.getDeltaMovement();
            p.setDeltaMovement(v.x * keep, Math.min(v.y, 0) * keep, v.z * keep);
        }
        tickBeatClick(mc);
    }

    /** 試合中は同期済みの時計で拍のクリックを鳴らす（サーバー tick の粒度に縛られない）。 */
    private static void tickBeatClick(Minecraft mc) {
        if (!ClientState.BEAT.enabled() || !ClientState.inMatch() || !NCConfig.CLIENT.showBeat.get()) return;
        long idx = ClientState.BEAT.beatIndexAt(ClientState.serverNowMs());
        if (idx != ClientState.lastBeatIndex) {
            ClientState.lastBeatIndex = idx;
            ClientState.lastBeatAtMs = System.currentTimeMillis();
            float vol = NCConfig.CLIENT.beatClickVolume.get().floatValue();
            if (vol > 0) {
                boolean accent = Math.floorMod(idx, NCConfig.CLIENT.beatAccentEvery.get()) == 0;
                mc.getSoundManager().play(SimpleSoundInstance.forUI(NCSounds.BEAT.get(), accent ? 1.25f : 1.0f, accent ? vol * 1.5f : vol));
            }
        }
    }

    /** 剣を持っているときの左クリックは「溜め斬り」に置き換える（判定はサーバー）。 */
    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isAttack() || mc.player == null || !SwordService.isHoldingBlade(mc.player)) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        // ブロックを押しっぱなしだと毎 tick 発火するので、押した瞬間だけ送る
        if (!attackHeldLastTick) {
            NCNetwork.sendToServer(new C2SActionPacket(C2SActionPacket.Action.SWORD, 0));
        }
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (ClientState.hitStopTicks <= 0) return;
        Input in = event.getInput();
        float keep = NCConfig.CLIENT.hitStopInputFreeze.get().floatValue();
        in.forwardImpulse *= keep;
        in.leftImpulse *= keep;
        in.jumping = false;
    }

    /** 0..1：スロー演出の強さ（出入りをフェード）。 */
    static float slowMoAmount(float partialTick) {
        if (ClientState.slowMoTicks <= 0) return 0f;
        int fade = NCConfig.CLIENT.slowMoFadeTicks.get();
        float t = ClientState.slowMoTicks - partialTick;
        float in = ClientState.slowMoTotal - t;
        float f = 1f;
        if (fade > 0) {
            f = Math.min(1f, Math.min(t / fade, (in + 1) / fade));
        }
        return Math.max(0f, f) * ClientState.slowMoStrength;
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        float a = slowMoAmount((float) event.getPartialTick());
        if (a > 0) {
            double m = NCConfig.CLIENT.slowMoFovMultiplier.get();
            event.setFOV(event.getFOV() * (1.0 + (m - 1.0) * a));
        }
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (ClientState.shakeTicks <= 0 || ClientState.shakeTotal <= 0) return;
        float decay = (ClientState.shakeTicks - (float) event.getPartialTick()) / ClientState.shakeTotal;
        float mag = ClientState.shakeDegrees * Math.max(0, decay);
        double t = (System.nanoTime() / 1.0e6) * 0.07;
        event.setYaw(event.getYaw() + (float) (Math.sin(t * 2.3) * mag));
        event.setPitch(event.getPitch() + (float) (Math.cos(t * 3.1) * mag * 0.7));
        event.setRoll(event.getRoll() + (float) (Math.sin(t * 1.7) * mag * 0.5));
    }

    /** スロー中は効果音のピッチを下げる（BGM とループ音は対象外）。 */
    @SubscribeEvent
    public static void onSound(PlaySoundEvent event) {
        SoundInstance s = event.getSound();
        if (s == null || s instanceof TickableSoundInstance || s.getSource() == SoundSource.MUSIC || s instanceof PitchedSound) return;
        float a = slowMoAmount(0);
        if (a <= 0) return;
        float pitch = (float) (1.0 + (NCConfig.CLIENT.slowMoSoundPitch.get() - 1.0) * a);
        event.setSound(new PitchedSound(s, pitch));
    }

    @SubscribeEvent
    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        float a = slowMoAmount(event.getPartialTick());
        if (a > 0.05f && NCConfig.CLIENT.slowMoDesaturate.get()) {
            SlowMoShader.process(event.getPartialTick());
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientState.reset();
        BeamRenderer.clear();
        SlowMoShader.close();
    }
}
