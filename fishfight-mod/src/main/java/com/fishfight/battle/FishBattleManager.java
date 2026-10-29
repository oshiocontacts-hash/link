package com.fishfight.battle;

import com.fishfight.FishFight;
import com.fishfight.fish.FishGenerator;
import com.fishfight.fish.FishRarity;
import com.fishfight.fish.FishSpecies;
import com.fishfight.fish.FishStats;
import com.fishfight.item.FishItem;
import com.fishfight.registry.ModComponents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 対戦の申し込み・受付と、進行中のバトルの管理。
 */
public final class FishBattleManager {
	private static final int CHALLENGE_TIMEOUT = 20 * 60;
	private static final double MAX_DISTANCE = 24.0;

	private record Challenge(UUID challenger, int tick) {
	}

	/** 申し込まれた側のUUID → 申し込み */
	private static final Map<UUID, Challenge> CHALLENGES = new HashMap<>();
	private static final List<FishBattle> BATTLES = new ArrayList<>();

	private FishBattleManager() {
	}

	@Nullable
	public static FishStats heldFish(ServerPlayer player) {
		ItemStack stack = player.getMainHandItem();
		if (!(stack.getItem() instanceof FishItem)) {
			return null;
		}
		return stack.get(ModComponents.FISH_STATS);
	}

	public static boolean inBattle(UUID player) {
		for (FishBattle battle : BATTLES) {
			if (battle.involves(player)) {
				return true;
			}
		}
		return false;
	}

	public static void challenge(ServerPlayer challenger, ServerPlayer target) {
		if (challenger == target) {
			fail(challenger, "battle.err.self");
			return;
		}
		FishStats fish = heldFish(challenger);
		if (fish == null) {
			fail(challenger, "battle.err.no_fish");
			return;
		}
		if (inBattle(challenger.getUUID()) || inBattle(target.getUUID())) {
			fail(challenger, "battle.err.busy");
			return;
		}
		if (challenger.level() != target.level() || challenger.distanceTo(target) > MAX_DISTANCE) {
			fail(challenger, "battle.err.too_far");
			return;
		}
		int now = challenger.server.getTickCount();
		Challenge existing = CHALLENGES.get(target.getUUID());
		if (existing != null && existing.challenger().equals(challenger.getUUID()) && now - existing.tick() < 60) {
			return; // 右クリック連打対策
		}
		CHALLENGES.put(target.getUUID(), new Challenge(challenger.getUUID(), now));

		challenger.sendSystemMessage(FishFight.tr("battle.challenge_sent", target.getDisplayName()).withStyle(ChatFormatting.AQUA));
		target.sendSystemMessage(FishFight.tr("battle.challenge_received", challenger.getDisplayName(), fish.displayName(), fish.power())
				.withStyle(ChatFormatting.AQUA));
		MutableComponent accept = FishFight.tr("battle.button.accept").withStyle(style -> style
				.withColor(ChatFormatting.GREEN)
				.withBold(true)
				.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/fishfight accept"))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, FishFight.tr("battle.button.accept_hover"))));
		MutableComponent deny = FishFight.tr("battle.button.deny").withStyle(style -> style
				.withColor(ChatFormatting.RED)
				.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/fishfight deny")));
		target.sendSystemMessage(Component.literal("  ").append(accept).append(Component.literal("  ")).append(deny));
		target.level().playSound(null, target.getX(), target.getY(), target.getZ(),
				SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 0.6f);
	}

	public static void accept(ServerPlayer target) {
		Challenge challenge = CHALLENGES.remove(target.getUUID());
		if (challenge == null) {
			fail(target, "battle.err.no_challenge");
			return;
		}
		ServerPlayer challenger = target.server.getPlayerList().getPlayer(challenge.challenger());
		if (challenger == null) {
			fail(target, "battle.err.offline");
			return;
		}
		FishStats a = heldFish(challenger);
		FishStats b = heldFish(target);
		if (b == null) {
			CHALLENGES.put(target.getUUID(), challenge);
			fail(target, "battle.err.no_fish");
			return;
		}
		if (a == null) {
			fail(target, "battle.err.challenger_no_fish");
			fail(challenger, "battle.err.no_fish");
			return;
		}
		if (inBattle(challenger.getUUID()) || inBattle(target.getUUID())) {
			fail(target, "battle.err.busy");
			return;
		}
		if (challenger.level() != target.level() || challenger.distanceTo(target) > MAX_DISTANCE) {
			fail(target, "battle.err.too_far");
			return;
		}

		Vec3 pa = challenger.position();
		Vec3 pb = target.position();
		Vec3 axis = new Vec3(pb.x - pa.x, 0, pb.z - pa.z);
		if (axis.lengthSqr() < 0.25) {
			Vec3 look = challenger.getLookAngle();
			axis = new Vec3(look.x, 0, look.z);
		}
		Vec3 center = new Vec3((pa.x + pb.x) / 2, Math.max(pa.y, pb.y) + 1.0, (pa.z + pb.z) / 2);
		Fighter fa = new Fighter(a, challenger.getUUID(), FishFight.tr("battle.owned", challenger.getDisplayName(), a.displayName()));
		Fighter fb = new Fighter(b, target.getUUID(), FishFight.tr("battle.owned", target.getDisplayName(), b.displayName()));
		BATTLES.add(new FishBattle(challenger.serverLevel(), center, axis, fa, fb, false));
	}

	public static void deny(ServerPlayer target) {
		Challenge challenge = CHALLENGES.remove(target.getUUID());
		if (challenge == null) {
			fail(target, "battle.err.no_challenge");
			return;
		}
		target.sendSystemMessage(FishFight.tr("battle.denied_self").withStyle(ChatFormatting.GRAY));
		ServerPlayer challenger = target.server.getPlayerList().getPlayer(challenge.challenger());
		if (challenger != null) {
			challenger.sendSystemMessage(FishFight.tr("battle.denied", target.getDisplayName()).withStyle(ChatFormatting.GRAY));
		}
	}

	/** 野生の魚と練習試合 */
	public static void practice(ServerPlayer player) {
		FishStats mine = heldFish(player);
		if (mine == null) {
			fail(player, "battle.err.no_fish");
			return;
		}
		if (inBattle(player.getUUID())) {
			fail(player, "battle.err.busy");
			return;
		}
		RandomSource random = player.getRandom();
		FishRarity rarity = FishRarity.byOrdinal(mine.rarity().ordinal() + random.nextInt(3) - 1);
		FishSpecies species = FishSpecies.values()[random.nextInt(FishSpecies.values().length)];
		FishStats wild = FishGenerator.generate(random, species, rarity, "");

		Vec3 look = player.getLookAngle();
		Vec3 forward = new Vec3(look.x, 0, look.z);
		forward = forward.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : forward.normalize();
		Vec3 center = player.position().add(forward.scale(4.0)).add(0, 1.0, 0);
		// プレイヤーから横向きに見えるように並べる
		Vec3 axis = new Vec3(-forward.z, 0, forward.x);

		Fighter fa = new Fighter(mine, player.getUUID(), FishFight.tr("battle.owned", player.getDisplayName(), mine.displayName()));
		Fighter fb = new Fighter(wild, null, FishFight.tr("battle.wild", wild.displayName()));
		BATTLES.add(new FishBattle(player.serverLevel(), center, axis, fa, fb, true));
	}

	public static void tick(MinecraftServer server) {
		if (!BATTLES.isEmpty()) {
			BATTLES.removeIf(FishBattle::tick);
		}
		int now = server.getTickCount();
		Iterator<Map.Entry<UUID, Challenge>> it = CHALLENGES.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Challenge> entry = it.next();
			if (now - entry.getValue().tick() > CHALLENGE_TIMEOUT) {
				it.remove();
				ServerPlayer challenger = server.getPlayerList().getPlayer(entry.getValue().challenger());
				if (challenger != null) {
					challenger.sendSystemMessage(FishFight.tr("battle.expired").withStyle(ChatFormatting.GRAY));
				}
			}
		}
	}

	public static void onDisconnect(ServerPlayer player) {
		CHALLENGES.remove(player.getUUID());
		CHALLENGES.values().removeIf(c -> c.challenger().equals(player.getUUID()));
	}

	public static void clear() {
		for (FishBattle battle : BATTLES) {
			battle.cancel();
		}
		BATTLES.clear();
		CHALLENGES.clear();
	}

	private static void fail(ServerPlayer player, String key) {
		player.sendSystemMessage(FishFight.tr(key).withStyle(ChatFormatting.RED));
	}
}
