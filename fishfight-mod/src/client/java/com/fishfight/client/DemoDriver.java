package com.fishfight.client;

import com.fishfight.FishFight;
import com.fishfight.entity.BattleFishEntity;
import com.fishfight.fish.FishSpecies;
import com.fishfight.item.FishItem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * 録画用のデモ操作（-Dfishfight.demo=true の時だけ動く）。
 * ワールドを作り、釣りミニゲームを2回、練習バトルを2回自動で行ってからゲームを終了する。
 * 画面は demo_frames/ に連番PNGで保存し、終了時に demo_done ファイルを作る。
 */
final class DemoDriver {
	private static final String WORLD = "fishfight-demo";
	private static final int MAX_TICKS = 20 * 60 * 9;
	/** 実時間で10fps。スローモーション中もそのまま遅く見えるように、tickではなく実時間で数える */
	private static final long FRAME_MILLIS = 100;
	private static long recordStart;

	private static final ExecutorService FRAME_WRITER = Executors.newSingleThreadExecutor();
	private static int frameNumber;
	private static boolean capturing;

	private record Step(String name, int delay, Predicate<Minecraft> ready, Consumer<Minecraft> action) {
	}

	private static final List<Step> STEPS = new ArrayList<>();
	private static int stage;
	private static int stageTicks;
	private static int stepIndex;
	private static int stepTicks;
	private static int battleTicks;
	private static int totalTicks;

	private DemoDriver() {
	}

	static void init() {
		buildScript();
		ClientTickEvents.END_CLIENT_TICK.register(DemoDriver::tick);
	}

	private static void buildScript() {
		step("setup", 60, mc -> true, mc -> {
			command(mc, "gamerule doDaylightCycle false");
			command(mc, "gamerule doWeatherCycle false");
			command(mc, "gamerule doMobSpawning false");
			command(mc, "time set 1000");
			command(mc, "weather clear");
			command(mc, "tp @s ~ 120 ~");
			command(mc, "fill ~-10 119 ~-10 ~10 119 ~10 minecraft:smooth_quartz");
			command(mc, "fill ~-10 120 ~-10 ~10 130 ~10 minecraft:air");
			// 魚が落ちないようにガラスの柵で囲む
			command(mc, "fill ~-10 120 ~-10 ~10 120 ~10 minecraft:glass");
			command(mc, "fill ~-9 120 ~-9 ~9 120 ~9 minecraft:air");
			command(mc, "fill ~-4 119 ~7 ~4 119 ~9 minecraft:water");
			command(mc, "tp @s ~ 120 ~-7 0 15");
		});
		step("title", 40, mc -> true, mc -> {
			capturing = true;
			mc.gui.getChat().clearMessages(false);
			command(mc, "title @s times 10 60 10");
			command(mc, "title @s subtitle \"釣りバトルMod テスト\"");
			command(mc, "title @s title \"Fish Fight!\"");
		});
		step("rod", 90, mc -> true, mc -> {
			command(mc, "give @s minecraft:fishing_rod");
			mc.player.getInventory().selected = 0;
		});
		step("cast", 30, mc -> true, DemoDriver::cast);
		step("catch taiyaki", 60, mc -> true, mc -> command(mc, "fishfight testcatch taiyaki common"));
		step("wait minigame", 40, mc -> mc.screen == null, mc -> { });
		step("give fish", 40, mc -> true, mc -> {
			command(mc, "fishfight give porcupinefish common");
			command(mc, "fishfight give doctor_fish legendary");
		});
		// ハリセンボン vs イワシの群れ（倒されたら針千本）
		battle(1, FishSpecies.PORCUPINEFISH, "sardine epic");
		// ドクターフィッシュ vs チンアナゴ（回復させて昇天させる／地面から動かない）
		battle(2, FishSpecies.DOCTOR_FISH, "garden_eel common");
		// タイ焼き vs ブリ（かじられる／出世する）
		battle(3, FishSpecies.TAIYAKI, "yellowtail rare");
		step("finish", 40, mc -> true, DemoDriver::finish);
	}

	/** 手持ちの魚を選んで、指定した相手と練習試合し、終わるまで待つ */
	private static void battle(int n, FishSpecies mine, String opponent) {
		step("select " + n, 40, mc -> true, mc -> select(mc, mine));
		step("battle " + n, 10, mc -> true, mc -> {
			mc.gui.getChat().clearMessages(false);
			// 前のバトルで下がった分を戻してから始める
			command(mc, n == 1 ? "tp @s ~ ~ ~ 0 18" : "tp @s ~ ~ ~2 0 18");
			command(mc, "fishfight practice " + opponent);
			battleTicks = 0;
		});
		step("step back " + n, 10, mc -> true, DemoDriver::stepBack);
		step("wait battle " + n, 100, DemoDriver::battleOver, mc -> { });
	}

	private static void step(String name, int delay, Predicate<Minecraft> ready, Consumer<Minecraft> action) {
		STEPS.add(new Step(name, delay, ready, action));
	}

	private static void tick(Minecraft mc) {
		totalTicks++;
		if (totalTicks > MAX_TICKS) {
			FishFight.LOGGER.error("[demo] timed out at stage {} step {}", stage, stepIndex);
			finish(mc);
			return;
		}
		switch (stage) {
			case 0 -> {
				// タイトル画面（またはその前の案内画面）が出たらワールドを作る
				if (mc.getOverlay() == null && mc.screen != null && ++stageTicks > 40) {
					FishFight.LOGGER.info("[demo] creating world from {}", mc.screen.getClass().getSimpleName());
					createWorld(mc);
					stage = 1;
				}
			}
			case 1 -> {
				if (mc.player != null && mc.level != null && mc.screen == null) {
					stage = 2;
					marker(mc, "demo_recording");
					FishFight.LOGGER.info("[demo] in world, starting script");
				}
			}
			case 2 -> {
				runScript(mc);
				followBattle(mc);
				if (capturing && stage == 2) {
					captureFrame(mc);
				}
			}
			default -> {
			}
		}
	}

	private static void runScript(Minecraft mc) {
		if (mc.player == null || stepIndex >= STEPS.size()) {
			return;
		}
		battleTicks++;
		Step step = STEPS.get(stepIndex);
		if (++stepTicks < step.delay() || !step.ready().test(mc)) {
			return;
		}
		FishFight.LOGGER.info("[demo] step {}", step.name());
		step.action().accept(mc);
		stepIndex++;
		stepTicks = 0;
	}

	/** バトル中はカメラで2匹の真ん中を追いかける */
	private static void followBattle(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			return;
		}
		double x = 0;
		double y = 0;
		double z = 0;
		int count = 0;
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (entity instanceof BattleFishEntity fish && !fish.isRemoved()) {
				x += fish.getX();
				y += fish.getY() + fish.getBbHeight() * 0.5;
				z += fish.getZ();
				count++;
			}
		}
		if (count == 0) {
			return;
		}
		double dx = x / count - mc.player.getX();
		double dy = y / count - mc.player.getEyeY();
		double dz = z / count - mc.player.getZ();
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
		float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
		mc.player.setYRot(Mth.rotLerp(0.15f, mc.player.getYRot(), yaw));
		mc.player.setXRot(Mth.lerp(0.15f, mc.player.getXRot(), pitch));
	}

	private static void createWorld(Minecraft mc) {
		LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
				new GameRules(), WorldDataConfiguration.DEFAULT);
		mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261030L, false, false),
				WorldPresets::createNormalWorldDimensions, mc.screen);
	}

	private static void command(Minecraft mc, String command) {
		mc.player.connection.sendCommand(command);
	}

	/** バトルが始まったら手を空にして、少し下がって全体を映す */
	private static void stepBack(Minecraft mc) {
		mc.player.getInventory().selected = 8;
		command(mc, "tp @s ~ ~ ~-2");
	}

		private static void cast(Minecraft mc) {
		mc.player.getInventory().selected = 0;
		mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
	}

	private static void select(Minecraft mc, FishSpecies species) {
		for (int i = 0; i < 9; i++) {
			if (mc.player.getInventory().getItem(i).getItem() instanceof FishItem fish && fish.species() == species) {
				mc.player.getInventory().selected = i;
				return;
			}
		}
		FishFight.LOGGER.warn("[demo] no {} in hotbar", species.id);
	}

	/** バトル用の魚が一度出て、全部いなくなったら終わり */
	private static boolean battleOver(Minecraft mc) {
		if (battleTicks < 100) {
			return false;
		}
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (entity instanceof BattleFishEntity) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 直前に描画されたフレームを読み出し、PNG書き出しは別スレッドで行う。
	 * 実時間で足りない分だけ同じ画面を複製するので、動画の速さは実際の見た目どおりになる。
	 */
	private static void captureFrame(Minecraft mc) {
		long now = System.currentTimeMillis();
		if (recordStart == 0) {
			recordStart = now;
		}
		int target = (int) ((now - recordStart) / FRAME_MILLIS) + 1;
		int count = target - frameNumber;
		if (count <= 0) {
			return;
		}
		NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget());
		Path dir = mc.gameDirectory.toPath().resolve("demo_frames");
		int first = frameNumber;
		frameNumber += count;
		FRAME_WRITER.execute(() -> {
			try (image) {
				Files.createDirectories(dir);
				Path firstPath = dir.resolve(String.format("%05d.png", first));
				image.writeToFile(firstPath);
				for (int i = 1; i < count; i++) {
					Files.copy(firstPath, dir.resolve(String.format("%05d.png", first + i)));
				}
			} catch (IOException e) {
				FishFight.LOGGER.warn("[demo] could not write frame {}", first, e);
			}
		});
	}

	private static void finish(Minecraft mc) {
		FishFight.LOGGER.info("[demo] finished, {} frames", frameNumber);
		capturing = false;
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
			Path path = mc.gameDirectory.toPath().resolve(name);
			Files.writeString(path, String.valueOf(System.currentTimeMillis()));
		} catch (IOException e) {
			FishFight.LOGGER.warn("[demo] could not write marker {}", name, e);
		}
	}
}
