package com.fishfight.battle;

import com.fishfight.FishFight;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * ワールドごとの対戦成績（world/fishfight_ranking.json）。
 */
public final class FishFightRanking {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static final class Entry {
		public String name = "";
		public int wins;
		public int losses;
		public int mobKills;
	}

	private static Map<String, Entry> entries;
	@Nullable
	private static Path path;

	private FishFightRanking() {
	}

	private static Map<String, Entry> load(MinecraftServer server) {
		if (entries == null) {
			path = server.getWorldPath(LevelResource.ROOT).resolve("fishfight_ranking.json");
			entries = new HashMap<>();
			if (Files.exists(path)) {
				try {
					Map<String, Entry> loaded = GSON.fromJson(Files.readString(path), new TypeToken<Map<String, Entry>>() { }.getType());
					if (loaded != null) {
						entries.putAll(loaded);
					}
				} catch (IOException | RuntimeException e) {
					FishFight.LOGGER.warn("Could not read {}", path, e);
				}
			}
		}
		return entries;
	}

	private static Entry entry(ServerPlayer player) {
		Entry entry = load(player.server).computeIfAbsent(player.getUUID().toString(), k -> new Entry());
		entry.name = player.getGameProfile().getName();
		return entry;
	}

	public static void recordPvp(ServerPlayer winner, ServerPlayer loser) {
		entry(winner).wins++;
		entry(loser).losses++;
		save();
	}

	public static void recordMob(ServerPlayer player, boolean won) {
		Entry entry = entry(player);
		if (won) {
			entry.mobKills++;
		} else {
			entry.losses++;
		}
		save();
	}

	/** 勝利数（モブ撃破も含む）の多い順 */
	public static List<Entry> top(MinecraftServer server, int limit) {
		List<Entry> list = new ArrayList<>(load(server).values());
		list.sort(Comparator.comparingInt((Entry e) -> e.wins + e.mobKills).reversed().thenComparingInt(e -> e.losses));
		return list.subList(0, Math.min(limit, list.size()));
	}

	@Nullable
	public static Entry get(MinecraftServer server, UUID player) {
		return load(server).get(player.toString());
	}

	private static void save() {
		if (path == null || entries == null) {
			return;
		}
		try {
			Files.writeString(path, GSON.toJson(entries));
		} catch (IOException e) {
			FishFight.LOGGER.warn("Could not save {}", path, e);
		}
	}

	/** サーバー（ワールド）を閉じたら読み直せるようにする */
	public static void unload() {
		entries = null;
		path = null;
	}
}
