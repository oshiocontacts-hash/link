package com.fishfight;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * 設定（config/fishfight.json）。/fishfight settings で変更でき、録画しやすいように調整できる。
 */
public final class FishFightConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("fishfight.json");
	private static FishFightConfig instance;

	/** 魚がかかった時にミニゲームになる確率（%）。100にすると毎回ミニゲーム */
	public int minigameChance = 85;
	/** とどめの一撃でスローモーションにする（サーバー全体が一瞬スローになる） */
	public boolean slowmo = true;
	/** バトルの流れをチャットに出す。false ならアクションバーに出して画面をすっきりさせる */
	public boolean chatLog = true;
	/** 実況のツッコミを入れる */
	public boolean announcer = true;

	public static FishFightConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static FishFightConfig load() {
		if (Files.exists(PATH)) {
			try {
				FishFightConfig config = GSON.fromJson(Files.readString(PATH), FishFightConfig.class);
				if (config != null) {
					return config;
				}
			} catch (IOException | RuntimeException e) {
				FishFight.LOGGER.warn("Could not read {}, using defaults", PATH, e);
			}
		}
		return new FishFightConfig();
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			Files.writeString(PATH, GSON.toJson(this));
		} catch (IOException e) {
			FishFight.LOGGER.warn("Could not save {}", PATH, e);
		}
	}
}
