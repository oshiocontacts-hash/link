package com.fishfight.fishing;

import com.fishfight.FishFight;
import com.fishfight.fish.FishRarity;
import com.fishfight.fish.FishStats;
import com.fishfight.fish.Mutation;
import com.fishfight.item.FishItem;
import com.fishfight.network.StartMinigamePayload;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * 魚がかかってからミニゲームの結果が返ってくるまでを管理する。
 * どの魚がかかったかはサーバー側で先に決めておく。
 */
public final class FishingMinigameManager {
	/** これより早くクリアしたら不正とみなす */
	private static final int MIN_TICKS = 30;
	private static final int TIMEOUT_TICKS = 20 * 90;

	private record Pending(int session, FishStats stats, int startTick) {
	}

	private static final Map<UUID, Pending> PENDING = new HashMap<>();
	private static int nextSession = 1;

	private FishingMinigameManager() {
	}

	public static void start(ServerPlayer player, FishStats stats) {
		int session = nextSession++;
		PENDING.put(player.getUUID(), new Pending(session, stats, player.server.getTickCount()));
		float difficulty = stats.species().difficulty + stats.rarity().difficultyBonus;
		ServerPlayNetworking.send(player, new StartMinigamePayload(session, stats.species().id,
				stats.rarity().ordinal(), difficulty, stats.has(Mutation.ARMS)));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 1.0f, 0.8f);
		player.displayClientMessage(FishFight.tr("fishing.hooked").withStyle(ChatFormatting.AQUA), true);
	}

	public static void handleResult(ServerPlayer player, int session, boolean success) {
		Pending pending = PENDING.get(player.getUUID());
		if (pending == null || pending.session() != session) {
			return;
		}
		PENDING.remove(player.getUUID());
		int elapsed = player.server.getTickCount() - pending.startTick();
		if (success && elapsed < MIN_TICKS) {
			success = false;
		}

		ServerLevel level = player.serverLevel();
		if (!success) {
			player.displayClientMessage(FishFight.tr("fishing.escaped").withStyle(ChatFormatting.GRAY), true);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.FISH_SWIM, SoundSource.PLAYERS, 1.0f, 1.0f);
			return;
		}

		FishStats stats = pending.stats();
		player.getInventory().placeItemBackInInventory(FishItem.create(stats));
		player.sendSystemMessage(FishFight.tr("fishing.caught", stats.displayName(),
				String.format(Locale.ROOT, "%.1f", stats.size()),
				String.format(Locale.ROOT, "%.2f", stats.weight())).withStyle(ChatFormatting.WHITE));
		if (stats.has(Mutation.ARMS)) {
			player.sendSystemMessage(FishFight.tr("fishing.caught_arms").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
		}
		player.giveExperiencePoints(3 + stats.rarity().ordinal() * 4);

		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.2f + stats.rarity().ordinal() * 0.1f);
		level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.5, 0.5, 0.5, 0.2);

		if (stats.rarity().isAtLeast(FishRarity.LEGENDARY)) {
			level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.6, 0.8, 0.6, 0.4);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
			player.server.getPlayerList().broadcastSystemMessage(
					FishFight.tr("fishing.broadcast", player.getDisplayName(), stats.rarity().displayName(), stats.displayName())
							.withStyle(ChatFormatting.GOLD), false);
		}
	}

	public static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
		while (it.hasNext()) {
			if (now - it.next().getValue().startTick() > TIMEOUT_TICKS) {
				it.remove();
			}
		}
	}

	public static void remove(ServerPlayer player) {
		PENDING.remove(player.getUUID());
	}

	public static void clear() {
		PENDING.clear();
	}
}
