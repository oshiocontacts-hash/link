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
	private static final int MAX_TICKS = 20 * 60 * 8;
	/** 2tickごと＝10fpsで画面を保存する */
	private static final int FRAME_INTERVAL = 2;

	private static final ExecutorService FRAME_WRITER = Executors.newSingleThreadExecutor();
	private static int frameNumber;

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
			command(mc, "fill ~-4 119 ~0 ~4 119 ~8 minecraft:water");
			command(mc, "fill ~-10 120 ~-10 ~10 130 ~10 minecraft:air");
			command(mc, "tp @s ~ 120 ~-7 0 12");
		});
		step("title", 40, mc -> true, mc -> {
			mc.gui.getChat().clearMessages(false);
			command(mc, "title @s times 10 60 10");
			command(mc, "title @s subtitle \"釣りバトルMod テスト\"");
			command(mc, "title @s title \"Fish Fight!\"");
		});
		step("rod", 90, mc -> true, mc -> {
			command(mc, "give @s minecraft:fishing_rod");
			mc.player.getInventory().selected = 0;
		});
		step("cast 1", 30, mc -> true, DemoDriver::cast);
		step("catch shark", 60, mc -> true, mc -> command(mc, "fishfight testcatch shark epic arms"));
		step("wait minigame 1", 40, mc -> mc.screen == null, mc -> { });
		step("cast 2", 50, mc -> true, DemoDriver::cast);
		step("catch tuna", 60, mc -> true, mc -> command(mc, "fishfight testcatch tuna rare"));
		step("wait minigame 2", 40, mc -> mc.screen == null, mc -> { });
		// 持ち替えはサーバーに次のtickで届くので、コマンドとは別のステップにする
		step("select tuna", 40, mc -> true, mc -> select(mc, FishSpecies.TUNA));
		step("show tuna", 10, mc -> true, mc -> {
			mc.gui.getChat().clearMessages(false);
			command(mc, "fishfight info");
		});
		step("battle 1", 120, mc -> true, mc -> {
			mc.gui.getChat().clearMessages(false);
			command(mc, "tp @s ~ ~ ~ 0 8");
			command(mc, "fishfight practice");
			battleTicks = 0;
		});
		step("wait battle 1", 100, DemoDriver::battleOver, mc -> { });
		step("select shark", 40, mc -> true, mc -> select(mc, FishSpecies.SHARK));
		step("battle 2", 10, mc -> true, mc -> {
			mc.gui.getChat().clearMessages(false);
			command(mc, "fishfight practice");
			battleTicks = 0;
		});
		step("wait battle 2", 100, DemoDriver::battleOver, mc -> { });
		step("finish", 40, mc -> true, DemoDriver::finish);
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
				if (totalTicks % FRAME_INTERVAL == 0) {
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

	private static void createWorld(Minecraft mc) {
		LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
				new GameRules(), WorldDataConfiguration.DEFAULT);
		mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261030L, false, false),
				WorldPresets::createNormalWorldDimensions, mc.screen);
	}

	private static void command(Minecraft mc, String command) {
		mc.player.connection.sendCommand(command);
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

	/** 直前に描画されたフレームを読み出し、PNG書き出しは別スレッドで行う */
	private static void captureFrame(Minecraft mc) {
		NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget());
		Path path = mc.gameDirectory.toPath().resolve("demo_frames").resolve(String.format("%05d.png", frameNumber++));
		FRAME_WRITER.execute(() -> {
			try (image) {
				Files.createDirectories(path.getParent());
				image.writeToFile(path);
			} catch (IOException e) {
				FishFight.LOGGER.warn("[demo] could not write frame {}", path, e);
			}
		});
	}

	private static void finish(Minecraft mc) {
		FishFight.LOGGER.info("[demo] finished, {} frames", frameNumber);
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
