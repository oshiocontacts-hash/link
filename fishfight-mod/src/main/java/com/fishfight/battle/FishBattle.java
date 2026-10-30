package com.fishfight.battle;

import com.fishfight.FishFight;
import com.fishfight.entity.BattleFishEntity;
import com.fishfight.fish.FishStats;
import com.fishfight.fish.Skill;
import com.fishfight.registry.ModComponents;
import com.fishfight.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 魚モブ同士の1試合の審判役。モブを出してカウントダウンし、どちらかが倒れたら勝敗を決める。
 * 実際の戦闘（移動・攻撃・スキル）は {@link BattleFishEntity} が自分で行う。
 */
public class FishBattle {
	private static final int INTRO_TICKS = 60;
	private static final int FIGHT_TICKS = 20 * 90;
	private static final int ENDING_TICKS = 60;
	private static final double VIEW_RANGE = 48.0;
	private static final double FORFEIT_RANGE = 64.0;
	/** この時間どちらもHPが変わらなければ仕切り直す（水中と陸でにらみ合い等） */
	private static final int STALEMATE_TICKS = 200;

	private enum State { INTRO, FIGHT, ENDING, DONE }

	private final MinecraftServer server;
	private final ServerLevel level;
	private final Vec3 center;
	private final Fighter[] fighters;
	private final ServerBossEvent[] bars;
	private final boolean practice;

	private final Vec3[] startPositions = new Vec3[2];
	private State state = State.INTRO;
	private int timer;
	private float lastHpSum = -1f;
	private int idleTicks;
	@Nullable
	private Fighter winner;

	public FishBattle(ServerLevel level, Vec3 center, Vec3 axis, Fighter a, Fighter b, boolean practice) {
		this.server = level.getServer();
		this.level = level;
		this.center = center;
		this.fighters = new Fighter[] {a, b};
		this.practice = practice;

		Vec3 dir = new Vec3(axis.x, 0, axis.z);
		dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : dir.normalize();
		startPositions[0] = spawn(a, center.subtract(dir.scale(2.0)), dir);
		startPositions[1] = spawn(b, center.add(dir.scale(2.0)), dir.reverse());
		a.entity.setOpponent(b.entity);
		b.entity.setOpponent(a.entity);

		this.bars = new ServerBossEvent[2];
		for (int i = 0; i < 2; i++) {
			bars[i] = new ServerBossEvent(fighters[i].name, fighters[i].stats.rarity().barColor, BossEvent.BossBarOverlay.NOTCHED_10);
		}
		updateViewers();
		announce(FishFight.tr("battle.start", a.name, b.name).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
		for (Fighter f : fighters) {
			announce(FishFight.tr("battle.intro_stats", f.name, f.stats.power(), f.stats.hp(), f.stats.atk(), f.stats.def(), f.stats.spd())
					.withStyle(ChatFormatting.GRAY));
			announce(Component.literal("　").append(skillList(f.stats)));
		}
	}

	private static MutableComponent skillList(FishStats stats) {
		if (stats.skills().isEmpty()) {
			return FishFight.tr("tooltip.no_skills").withStyle(ChatFormatting.DARK_GRAY);
		}
		MutableComponent list = FishFight.tr("tooltip.skills").withStyle(ChatFormatting.GRAY);
		for (Skill skill : stats.skills()) {
			list.append(" ").append(skill.displayName());
		}
		return list;
	}

	private Vec3 spawn(Fighter f, Vec3 pos, Vec3 facing) {
		BattleFishEntity entity = ModEntities.BATTLE_FISH.create(level);
		if (entity == null) {
			throw new IllegalStateException("battle fish could not be created");
		}
		// 地面の高さに置く（空中や地中に出さない）
		BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(pos));
		double y = Math.abs(ground.getY() - pos.y) < 6 ? ground.getY() : pos.y;
		float yaw = (float) (Mth.atan2(facing.z, facing.x) * Mth.RAD_TO_DEG) - 90f;
		entity.moveTo(pos.x, y, pos.z, yaw, 0f);
		entity.setYHeadRot(yaw);
		entity.setYBodyRot(yaw);
		f.entity = entity;
		entity.setup(this, f);
		level.addFreshEntity(entity);
		level.sendParticles(ParticleTypes.SPLASH, pos.x, y + 0.5, pos.z, 40, 0.4, 0.3, 0.4, 0.3);
		return new Vec3(pos.x, y, pos.z);
	}

	public Vec3 center() {
		return center;
	}

	public boolean involves(UUID player) {
		for (Fighter f : fighters) {
			if (player.equals(f.owner)) {
				return true;
			}
		}
		return false;
	}

	/** @return 試合が終わって片付けが済んだら true */
	public boolean tick() {
		timer++;
		switch (state) {
			case INTRO -> tickIntro();
			case FIGHT -> tickFight();
			case ENDING -> tickEnding();
			case DONE -> {
				return true;
			}
		}
		if (timer % 20 == 0) {
			updateViewers();
		}
		return state == State.DONE;
	}

	private void tickIntro() {
		if (fighters[0].entity.isRemoved() || fighters[1].entity.isRemoved()) {
			cancel();
			return;
		}
		if (timer == 1 || timer == 21 || timer == 41) {
			String count = String.valueOf(3 - (timer - 1) / 20);
			titleAll(Component.literal(count).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
					FishFight.tr("battle.vs", fighters[0].name, fighters[1].name), 18);
			playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
		}
		if (timer >= INTRO_TICKS) {
			titleAll(FishFight.tr("battle.fight").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), Component.empty(), 20);
			playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.8f);
			for (Fighter f : fighters) {
				f.entity.setActive(true);
			}
			state = State.FIGHT;
			timer = 0;
		}
	}

	private void tickFight() {
		for (int i = 0; i < 2; i++) {
			bars[i].setProgress(fighters[i].hpRatio());
		}
		if (!checkParticipants()) {
			return;
		}
		boolean aDown = fighters[0].defeated();
		boolean bDown = fighters[1].defeated();
		if (aDown || bDown) {
			if (aDown && bDown) {
				finish(fighters[0].hpRatio() >= fighters[1].hpRatio() ? fighters[0] : fighters[1]);
			} else {
				finish(aDown ? fighters[1] : fighters[0]);
			}
		} else if (timer >= FIGHT_TICKS) {
			announce(FishFight.tr("battle.timeout").withStyle(ChatFormatting.GRAY));
			finish(fighters[0].hpRatio() >= fighters[1].hpRatio() ? fighters[0] : fighters[1]);
		} else {
			checkStalemate();
		}
	}

	private void tickEnding() {
		if (winner != null && !winner.defeated() && timer % 5 == 0) {
			Vec3 p = winner.entity.position();
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.x, p.y + 0.5, p.z, 4, 0.5, 0.5, 0.5, 0.0);
		}
		if (timer >= ENDING_TICKS) {
			cleanup();
		}
	}

	/** しばらくどちらもダメージを受けていなければ、最初の位置に戻して仕切り直す */
	private void checkStalemate() {
		float hpSum = fighters[0].entity.getHealth() + fighters[1].entity.getHealth();
		if (Math.abs(hpSum - lastHpSum) > 0.01f) {
			lastHpSum = hpSum;
			idleTicks = 0;
			return;
		}
		if (++idleTicks < STALEMATE_TICKS) {
			return;
		}
		idleTicks = 0;
		announce(FishFight.tr("battle.reset").withStyle(ChatFormatting.YELLOW));
		for (int i = 0; i < 2; i++) {
			BattleFishEntity entity = fighters[i].entity;
			Vec3 p = startPositions[i];
			entity.getNavigation().stop();
			entity.setDeltaMovement(Vec3.ZERO);
			entity.teleportTo(p.x, p.y, p.z);
			level.sendParticles(ParticleTypes.POOF, p.x, p.y + 0.5, p.z, 15, 0.3, 0.3, 0.3, 0.05);
		}
		playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.7f);
	}

	/** 持ち主がいなくなったら不戦敗 */
	private boolean checkParticipants() {
		for (Fighter f : fighters) {
			if (f.owner == null) {
				continue;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(f.owner);
			if (player == null || player.level() != level || player.position().distanceToSqr(center) > FORFEIT_RANGE * FORFEIT_RANGE) {
				announce(FishFight.tr("battle.forfeit", f.name).withStyle(ChatFormatting.GRAY));
				finish(other(f));
				return false;
			}
		}
		return true;
	}

	private void finish(Fighter winner) {
		if (state != State.FIGHT) {
			return;
		}
		state = State.ENDING;
		timer = 0;
		this.winner = winner;
		Fighter loser = other(winner);
		for (Fighter f : fighters) {
			if (!f.defeated()) {
				f.entity.setActive(false);
			}
		}
		for (int i = 0; i < 2; i++) {
			bars[i].setProgress(fighters[i].hpRatio());
		}
		announce(FishFight.tr("battle.defeated", loser.name).withStyle(ChatFormatting.GRAY));
		announce(FishFight.tr("battle.winner", winner.name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		if (!winner.defeated()) {
			Vec3 p = winner.entity.position();
			level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.x, p.y + 0.5, p.z, 50, 0.4, 0.4, 0.4, 0.3);
		}
		playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

		ServerPlayer winnerPlayer = winner.owner == null ? null : server.getPlayerList().getPlayer(winner.owner);
		ServerPlayer loserPlayer = loser.owner == null ? null : server.getPlayerList().getPlayer(loser.owner);
		if (winnerPlayer != null) {
			title(winnerPlayer, FishFight.tr("battle.victory").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), winner.name, 50);
			winnerPlayer.giveExperiencePoints(10 + loser.stats.rarity().ordinal() * 5);
			if (!practice) {
				addWin(winnerPlayer, winner.stats);
			}
		}
		if (loserPlayer != null) {
			title(loserPlayer, FishFight.tr("battle.defeat").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD), loser.name, 50);
		}
	}

	/** 勝った魚のアイテムに勝利数を記録する */
	private static void addWin(ServerPlayer player, FishStats stats) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stats.equals(stack.get(ModComponents.FISH_STATS))) {
				stack.set(ModComponents.FISH_STATS, stats.withWins(stats.wins() + 1));
				return;
			}
		}
	}

	public void cancel() {
		if (state != State.DONE) {
			announce(FishFight.tr("battle.cancelled").withStyle(ChatFormatting.GRAY));
		}
		cleanup();
	}

	private void cleanup() {
		state = State.DONE;
		for (Fighter f : fighters) {
			if (f.entity != null && !f.entity.isRemoved()) {
				Vec3 p = f.entity.position();
				level.sendParticles(ParticleTypes.POOF, p.x, p.y + 0.3, p.z, 15, 0.3, 0.3, 0.3, 0.05);
				f.entity.discard();
			}
		}
		for (ServerBossEvent bar : bars) {
			bar.removeAllPlayers();
		}
	}

	// ---- 表示まわり ----

	private Fighter other(Fighter f) {
		return f == fighters[0] ? fighters[1] : fighters[0];
	}

	private List<ServerPlayer> viewers() {
		List<ServerPlayer> result = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (involves(p.getUUID()) || p.position().distanceToSqr(center) < VIEW_RANGE * VIEW_RANGE) {
				result.add(p);
			}
		}
		return result;
	}

	private void updateViewers() {
		List<ServerPlayer> viewers = viewers();
		for (ServerBossEvent bar : bars) {
			for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) {
				if (!viewers.contains(p)) {
					bar.removePlayer(p);
				}
			}
			for (ServerPlayer p : viewers) {
				bar.addPlayer(p);
			}
		}
	}

	/** 観戦中のプレイヤー全員にチャットで知らせる */
	public void announce(Component message) {
		for (ServerPlayer p : viewers()) {
			p.sendSystemMessage(message);
		}
	}

	public void titleAll(Component title, Component subtitle, int stay) {
		for (ServerPlayer p : viewers()) {
			title(p, title, subtitle, stay);
		}
	}

	private static void title(ServerPlayer player, Component title, Component subtitle, int stay) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(3, stay, 8));
		player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		player.connection.send(new ClientboundSetTitleTextPacket(title));
	}

	private void playSound(SoundEvent sound, float volume, float pitch) {
		level.playSound(null, center.x, center.y, center.z, sound, SoundSource.PLAYERS, volume, pitch);
	}
}
