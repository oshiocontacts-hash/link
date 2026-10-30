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
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 対戦の申し込み・受付と、進行中のバトルの管理。
 */
public final class FishBattleManager {
	private static final int CHALLENGE_TIMEOUT = 20 * 60;
	private static final double MAX_DISTANCE = 24.0;

	private record Challenge(UUID challenger, int tick, boolean bet) {
	}

	/** 申し込まれた側のUUID → 申し込み */
	private static final Map<UUID, Challenge> CHALLENGES = new HashMap<>();
	private static final List<FishBattle> BATTLES = new ArrayList<>();

	/** スローモーション中なら元の速さ、そうでなければ -1 */
	private static float savedTickRate = -1f;
	private static long slowMotionEnd;

	private FishBattleManager() {
	}

	/** とどめの瞬間、2.5秒だけサーバー全体をスローにする */
	public static void slowMotion(MinecraftServer server) {
		if (savedTickRate < 0f) {
			savedTickRate = server.tickRateManager().tickrate();
		}
		server.tickRateManager().setTickRate(5f);
		slowMotionEnd = System.currentTimeMillis() + 2500;
	}

	private static void endSlowMotion(MinecraftServer server) {
		if (savedTickRate > 0f) {
			server.tickRateManager().setTickRate(savedTickRate);
			savedTickRate = -1f;
		}
	}

	/** 手に持った魚の表示名。金床で名前を付けていれば「名前」（種類）になる */
	public static Component fishLabel(ServerPlayer player, FishStats stats) {
		ItemStack stack = player.getMainHandItem();
		if (stack.has(DataComponents.CUSTOM_NAME)) {
			return FishFight.tr("battle.named", stack.getHoverName(), stats.displayName());
		}
		return stats.displayName();
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
		challenge(challenger, target, false);
	}

	public static void challenge(ServerPlayer challenger, ServerPlayer target, boolean bet) {
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
		// 相手からもう申し込まれていたら、右クリックし返しただけで即開始
		Challenge incoming = CHALLENGES.get(challenger.getUUID());
		if (incoming != null && incoming.challenger().equals(target.getUUID())) {
			accept(challenger);
			return;
		}
		Challenge existing = CHALLENGES.get(target.getUUID());
		if (existing != null && existing.challenger().equals(challenger.getUUID()) && now - existing.tick() < 60) {
			return; // 右クリック連打対策
		}
		CHALLENGES.put(target.getUUID(), new Challenge(challenger.getUUID(), now, bet));

		challenger.sendSystemMessage(FishFight.tr("battle.challenge_sent", target.getDisplayName()).withStyle(ChatFormatting.AQUA));
		target.sendSystemMessage(FishFight.tr("battle.challenge_received", challenger.getDisplayName(), fish.displayName(), fish.power())
				.withStyle(ChatFormatting.AQUA));
		if (bet) {
			target.sendSystemMessage(FishFight.tr("battle.bet_notice").withStyle(ChatFormatting.GOLD));
		}
		target.sendSystemMessage(FishFight.tr("battle.quick_accept_hint").withStyle(ChatFormatting.GRAY));
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
		Vec3 center = new Vec3((pa.x + pb.x) / 2, Math.max(pa.y, pb.y), (pa.z + pb.z) / 2);
		Fighter fa = new Fighter(a, challenger.getUUID(), FishFight.tr("battle.owned", challenger.getDisplayName(), fishLabel(challenger, a)));
		Fighter fb = new Fighter(b, target.getUUID(), FishFight.tr("battle.owned", target.getDisplayName(), fishLabel(target, b)));
		FishBattle battle = new FishBattle(challenger.serverLevel(), center, axis, fa, fb, false);
		battle.setBet(challenge.bet());
		BATTLES.add(battle);
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

	/** 敵モブに魚バトルを挑む。負けた方が死ぬ */
	public static void fightMob(ServerPlayer player, Mob mob) {
		FishStats mine = heldFish(player);
		if (mine == null) {
			fail(player, "battle.err.no_fish");
			return;
		}
		if (inBattle(player.getUUID()) || inBattle(mob.getUUID())) {
			fail(player, "battle.err.busy");
			return;
		}
		if (mob instanceof WitherBoss || mob instanceof EnderDragon || mob instanceof Warden || mob instanceof ElderGuardian) {
			fail(player, "battle.err.boss");
			return;
		}
		RandomSource random = player.getRandom();
		FishRarity rarity = FishRarity.byOrdinal(mine.rarity().ordinal() + random.nextInt(3) - 1);
		FishStats wild = FishGenerator.generate(random, mobFish(mob, random), rarity, "");

		Vec3 pp = player.position();
		Vec3 mp = mob.position();
		Vec3 axis = new Vec3(mp.x - pp.x, 0, mp.z - pp.z);
		if (axis.lengthSqr() < 0.25) {
			Vec3 look = player.getLookAngle();
			axis = new Vec3(look.x, 0, look.z);
		}
		Vec3 center = new Vec3((pp.x + mp.x) / 2, pp.y, (pp.z + mp.z) / 2);
		Fighter fa = new Fighter(mine, player.getUUID(), FishFight.tr("battle.owned", player.getDisplayName(), fishLabel(player, mine)));
		Fighter fb = new Fighter(wild, null, FishFight.tr("battle.owned", mob.getDisplayName(), wild.displayName()));
		FishBattle battle = new FishBattle(player.serverLevel(), center, axis, fa, fb, false);
		battle.setStakeMob(mob);
		BATTLES.add(battle);
	}

	/** モブごとにそれっぽい魚を出す */
	private static FishSpecies mobFish(Mob mob, RandomSource random) {
		if (mob instanceof Creeper) {
			return FishSpecies.PUFFERFISH;
		}
		if (mob instanceof Drowned || mob instanceof Guardian) {
			return FishSpecies.SHARK;
		}
		if (mob instanceof AbstractSkeleton) {
			return FishSpecies.PIRANHA;
		}
		if (mob instanceof Zombie) {
			return FishSpecies.CARP;
		}
		if (mob instanceof EnderMan) {
			return FishSpecies.OARFISH;
		}
		if (mob instanceof Witch) {
			return FishSpecies.SUNFISH;
		}
		if (mob instanceof Spider) {
			return FishSpecies.CATFISH;
		}
		return FishSpecies.values()[random.nextInt(FishSpecies.values().length)];
	}

	/** 手に持っている魚をみんなに自慢する（チャットでカーソルを合わせるとステータスが見える） */
	public static void show(ServerPlayer player) {
		ItemStack stack = player.getMainHandItem();
		if (heldFish(player) == null) {
			fail(player, "battle.err.no_fish");
			return;
		}
		Component item = Component.empty().append(stack.getHoverName()).withStyle(style -> style
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(stack))));
		player.server.getPlayerList().broadcastSystemMessage(
				FishFight.tr("show.message", player.getDisplayName(), Component.literal("[").append(item).append("]")), false);
	}

	/** 野生の魚と練習試合。相手を指定しなければランダム */
	public static void practice(ServerPlayer player, @Nullable FishStats opponent) {
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
		FishStats wild = opponent;
		if (wild == null) {
			FishRarity rarity = FishRarity.byOrdinal(mine.rarity().ordinal() + random.nextInt(3) - 1);
			FishSpecies species = FishSpecies.values()[random.nextInt(FishSpecies.values().length)];
			wild = FishGenerator.generate(random, species, rarity, "");
		}

		Vec3 look = player.getLookAngle();
		Vec3 forward = new Vec3(look.x, 0, look.z);
		forward = forward.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : forward.normalize();
		Vec3 center = player.position().add(forward.scale(5.0));
		// プレイヤーから横向きに見えるように並べる
		Vec3 axis = new Vec3(-forward.z, 0, forward.x);

		Fighter fa = new Fighter(mine, player.getUUID(), FishFight.tr("battle.owned", player.getDisplayName(), fishLabel(player, mine)));
		Fighter fb = new Fighter(wild, null, FishFight.tr("battle.wild", wild.displayName()));
		BATTLES.add(new FishBattle(player.serverLevel(), center, axis, fa, fb, true));
	}

	public static void tick(MinecraftServer server) {
		if (savedTickRate > 0f && System.currentTimeMillis() > slowMotionEnd) {
			endSlowMotion(server);
		}
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

	public static void clear(MinecraftServer server) {
		endSlowMotion(server);
		FishFightRanking.unload();
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
