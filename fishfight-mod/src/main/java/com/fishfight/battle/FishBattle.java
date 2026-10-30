package com.fishfight.battle;

import com.fishfight.FishFight;
import com.fishfight.entity.BattleFishEntity;
import com.fishfight.fish.FishSpecies;
import com.fishfight.fish.FishStats;
import com.fishfight.fish.Mutation;
import com.fishfight.registry.ModComponents;
import com.fishfight.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 魚同士のオートバトル1試合。サーバーtickごとに進む。
 * 素早さでゲージが溜まり、溜まった方が行動する。戦闘中にいきなり腕が生えることがある。
 */
public class FishBattle {
	private static final int INTRO_TICKS = 60;
	private static final int ENDING_TICKS = 60;
	private static final int ACTION_COOLDOWN = 28;
	private static final int GAUGE_MAX = 600;
	private static final int MAX_ACTIONS = 60;
	private static final double VIEW_RANGE = 48.0;
	private static final double FORFEIT_RANGE = 64.0;

	private enum State { INTRO, FIGHT, ENDING, DONE }

	private enum Move { TACKLE, RUSH, UPPERCUT, KICK, SPECIAL }

	private final MinecraftServer server;
	private final ServerLevel level;
	private final Vec3 center;
	private final Fighter[] fighters;
	private final ServerBossEvent[] bars;
	private final RandomSource random;
	private final boolean practice;

	private State state = State.INTRO;
	private int timer;
	private int cooldown;
	private int actions;
	@Nullable
	private Fighter winner;

	public FishBattle(ServerLevel level, Vec3 center, Vec3 axis, Fighter a, Fighter b, boolean practice) {
		this.server = level.getServer();
		this.level = level;
		this.center = center;
		this.fighters = new Fighter[] {a, b};
		this.random = level.getRandom();
		this.practice = practice;

		Vec3 dir = new Vec3(axis.x, 0, axis.z);
		dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : dir.normalize();
		spawn(a, center.subtract(dir.scale(1.8)), dir);
		spawn(b, center.add(dir.scale(1.8)), dir.reverse());

		this.bars = new ServerBossEvent[2];
		for (int i = 0; i < 2; i++) {
			bars[i] = new ServerBossEvent(fighters[i].name, fighters[i].stats.rarity().barColor, BossEvent.BossBarOverlay.NOTCHED_10);
		}
		updateViewers();
		say(FishFight.tr("battle.start", a.name, b.name).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
		for (Fighter f : fighters) {
			say(FishFight.tr("battle.intro_stats", f.name, f.stats.power(), f.stats.hp(), f.stats.atk(), f.stats.def(), f.stats.spd())
					.withStyle(ChatFormatting.GRAY));
		}
	}

	private void spawn(Fighter f, Vec3 pos, Vec3 facing) {
		BattleFishEntity entity = ModEntities.BATTLE_FISH.create(level);
		if (entity == null) {
			throw new IllegalStateException("battle fish could not be created");
		}
		entity.setup(f.stats);
		float yaw = (float) (Mth.atan2(facing.z, facing.x) * Mth.RAD_TO_DEG) - 90f;
		entity.moveTo(pos.x, pos.y, pos.z, yaw, 0f);
		entity.setYHeadRot(yaw);
		entity.setCustomNameVisible(true);
		f.entity = entity;
		updateName(f);
		level.addFreshEntity(entity);
		level.sendParticles(ParticleTypes.SPLASH, pos.x, pos.y, pos.z, 40, 0.4, 0.3, 0.4, 0.3);
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
		if (state != State.DONE && (fighters[0].entity.isRemoved() || fighters[1].entity.isRemoved())) {
			cancel();
			return true;
		}
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
		if (timer == 1 || timer == 21 || timer == 41) {
			String count = String.valueOf(3 - (timer - 1) / 20);
			titleAll(Component.literal(count).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
					FishFight.tr("battle.vs", fighters[0].name, fighters[1].name), 18);
			playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
		}
		if (timer >= INTRO_TICKS) {
			titleAll(FishFight.tr("battle.fight").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), Component.empty(), 20);
			playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.8f);
			state = State.FIGHT;
			timer = 0;
		}
	}

	private void tickFight() {
		if (!checkParticipants()) {
			return;
		}
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		for (Fighter f : fighters) {
			f.gauge += 30 + Math.round(f.spd());
		}
		Fighter actor = null;
		for (Fighter f : fighters) {
			if (f.gauge >= GAUGE_MAX && (actor == null || f.gauge > actor.gauge)) {
				actor = f;
			}
		}
		if (actor == null) {
			return;
		}
		actor.gauge -= GAUGE_MAX;
		Fighter target = other(actor);
		act(actor, target);
		actions++;
		cooldown = ACTION_COOLDOWN;
		updateBars();

		if (!target.alive()) {
			finish(actor);
		} else if (!actor.alive()) {
			finish(target);
		} else if (actions >= MAX_ACTIONS) {
			say(FishFight.tr("battle.timeout").withStyle(ChatFormatting.GRAY));
			finish(fighters[0].hpRatio() >= fighters[1].hpRatio() ? fighters[0] : fighters[1]);
		}
	}

	private void tickEnding() {
		if (winner != null && timer % 5 == 0) {
			Vec3 p = winner.entity.position();
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.x, p.y + 0.5, p.z, 4, 0.5, 0.5, 0.5, 0.0);
		}
		if (timer >= ENDING_TICKS) {
			cleanup();
		}
	}

	/** 持ち主がいなくなったら不戦敗 */
	private boolean checkParticipants() {
		for (Fighter f : fighters) {
			if (f.owner == null) {
				continue;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(f.owner);
			if (player == null || player.level() != level || player.position().distanceToSqr(center) > FORFEIT_RANGE * FORFEIT_RANGE) {
				say(FishFight.tr("battle.forfeit", f.name).withStyle(ChatFormatting.GRAY));
				f.hp = 0;
				updateName(f);
				finish(other(f));
				return false;
			}
		}
		return true;
	}

	// ---- 行動 ----

	private void act(Fighter actor, Fighter target) {
		if (actor.stunned) {
			actor.stunned = false;
			say(FishFight.tr("battle.stunned", actor.name).withStyle(ChatFormatting.YELLOW));
			particles(actor, ParticleTypes.ELECTRIC_SPARK, 20);
			return;
		}

		// いきなり腕が生える。追い詰められるほど生えやすい
		float sproutChance = 0.05f + 0.15f * (1f - actor.hpRatio());
		if (FishFight.DEMO) {
			sproutChance *= 3f;
		}
		if (!actor.arms && !actor.sproutedArms && random.nextFloat() < sproutChance) {
			sproutArms(actor);
		}
		if (!actor.legs && !actor.sproutedLegs && random.nextFloat() < 0.025f) {
			sproutLegs(actor);
		}

		switch (chooseMove(actor)) {
			case TACKLE -> {
				announce(actor, "tackle");
				hit(actor, target, 1.0f, false, 1.0f, 0, true);
			}
			case RUSH -> {
				announce(actor, "rush");
				multiHit(actor, target, 3 + random.nextInt(3), 0.38f);
			}
			case UPPERCUT -> {
				announce(actor, "uppercut");
				hit(actor, target, 1.5f, false, 0.9f, 20, true);
			}
			case KICK -> {
				announce(actor, "kick");
				hit(actor, target, 1.15f, false, 1.0f, 0, true);
				if (random.nextFloat() < 0.3f) {
					actor.dodgeNext = true;
					say(FishFight.tr("battle.kick_stance", actor.name).withStyle(ChatFormatting.GRAY));
				}
			}
			case SPECIAL -> special(actor, target);
		}
	}

	private Move chooseMove(Fighter actor) {
		if (actor.arms && random.nextFloat() < 0.4f) {
			return random.nextBoolean() ? Move.RUSH : Move.UPPERCUT;
		}
		if (actor.legs && random.nextFloat() < 0.2f) {
			return Move.KICK;
		}
		if (random.nextFloat() < 0.25f) {
			return Move.SPECIAL;
		}
		return Move.TACKLE;
	}

	private void special(Fighter actor, Fighter target) {
		FishSpecies species = actor.stats.species();
		announce(actor, species.id);
		switch (species) {
			case CARP -> {
				int heal = Math.max(1, Math.round(actor.maxHp * 0.15f));
				actor.hp = Math.min(actor.maxHp, actor.hp + heal);
				actor.atkMul *= 1.15f;
				updateName(actor);
				say(FishFight.tr("battle.effect.carp", actor.name, heal).withStyle(ChatFormatting.GREEN));
				particles(actor, ParticleTypes.HEART, 6);
			}
			case MACKEREL -> {
				hit(actor, target, 0.6f, false, 1.0f, 0, true);
				actor.dodgeNext = true;
				say(FishFight.tr("battle.effect.mackerel", actor.name).withStyle(ChatFormatting.GRAY));
			}
			case PUFFERFISH -> {
				if (actor.puffs < 2) {
					actor.puffs++;
					actor.defMul *= 1.4f;
					say(FishFight.tr("battle.effect.pufferfish", actor.name).withStyle(ChatFormatting.GREEN));
					playSound(SoundEvents.PUFFER_FISH_BLOW_UP, 1.0f, 1.0f);
				} else {
					hit(actor, target, 1.0f, false, 1.0f, 0, true);
				}
			}
			case PIRANHA -> multiHit(actor, target, 2 + random.nextInt(3), 0.45f);
			case CATFISH -> {
				hit(actor, target, 1.6f, false, 0.85f, 0, true);
				playSound(SoundEvents.RAVAGER_ROAR, 0.6f, 1.4f);
			}
			case TUNA -> multiHit(actor, target, 2, 0.7f);
			case MARLIN -> hit(actor, target, 1.4f, true, 1.0f, 0, true);
			case SHARK -> hit(actor, target, 2.1f, false, 0.7f, 0, true);
			case OARFISH -> {
				target.atkMul *= 0.8f;
				target.defMul *= 0.8f;
				say(FishFight.tr("battle.effect.oarfish", target.name).withStyle(ChatFormatting.DARK_PURPLE));
				particles(target, ParticleTypes.WITCH, 20);
				playSound(SoundEvents.BEACON_DEACTIVATE, 1.0f, 0.6f);
			}
			case COELACANTH -> hit(actor, target, 1.8f, false, 0.95f, 0, true);
		}
	}

	private void multiHit(Fighter actor, Fighter target, int count, float power) {
		int total = 0;
		int landed = 0;
		for (int i = 0; i < count && target.alive() && actor.alive(); i++) {
			int dealt = hit(actor, target, power, false, 1.0f, 0, false);
			if (dealt > 0) {
				total += dealt;
				landed++;
			}
		}
		if (landed == 0) {
			say(FishFight.tr("battle.dodge", target.name).withStyle(ChatFormatting.GRAY));
		} else {
			say(FishFight.tr("battle.multi_hit", landed, target.name, total).withStyle(ChatFormatting.WHITE));
		}
	}

	/**
	 * 1回分の攻撃。
	 * @return 与えたダメージ（外れたら0）
	 */
	private int hit(Fighter attacker, Fighter defender, float power, boolean ignoreDef, float accuracy, int extraCrit, boolean verbose) {
		attacker.entity.playAttack();
		if (random.nextFloat() > accuracy) {
			if (verbose) {
				say(FishFight.tr("battle.miss", attacker.name).withStyle(ChatFormatting.GRAY));
			}
			playSound(SoundEvents.PLAYER_ATTACK_NODAMAGE, 1.0f, 1.0f);
			return 0;
		}
		if (defender.dodgeNext) {
			defender.dodgeNext = false;
			if (verbose) {
				say(FishFight.tr("battle.dodge", defender.name).withStyle(ChatFormatting.GRAY));
			}
			particles(defender, ParticleTypes.CLOUD, 6);
			return 0;
		}
		float as = attacker.spd();
		float ds = defender.spd();
		float dodge = 0.05f + Math.max(0f, (ds - as) / Math.max(1f, ds + as)) * 0.3f;
		if (defender.stats.has(Mutation.TINY)) {
			dodge += 0.08f;
		}
		if (random.nextFloat() < Math.min(0.35f, dodge)) {
			if (verbose) {
				say(FishFight.tr("battle.dodge", defender.name).withStyle(ChatFormatting.GRAY));
			}
			particles(defender, ParticleTypes.CLOUD, 6);
			playSound(SoundEvents.PLAYER_ATTACK_NODAMAGE, 1.0f, 1.3f);
			return 0;
		}

		float damage = attacker.atk() * power * (0.85f + random.nextFloat() * 0.3f);
		if (!ignoreDef) {
			damage *= 100f / (100f + defender.def() * 2.5f);
		}
		boolean crit = random.nextFloat() * 100f < attacker.stats.crit() + extraCrit;
		if (crit) {
			damage *= 1.75f;
		}
		int dealt = Math.max(1, Math.round(damage));
		applyDamage(defender, dealt);
		defender.entity.playHurt();

		if (crit) {
			if (verbose) {
				say(FishFight.tr("battle.crit").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
			}
			particles(defender, ParticleTypes.CRIT, 15);
			playSound(SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 1.0f);
		} else {
			playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 1.0f);
		}
		particles(defender, ParticleTypes.DAMAGE_INDICATOR, Math.min(12, 1 + dealt / 3));
		if (verbose) {
			say(FishFight.tr("battle.damage", defender.name, dealt).withStyle(ChatFormatting.WHITE));
		}

		if (defender.stats.has(Mutation.SPIKY) && attacker.alive()) {
			int reflected = Math.max(1, Math.round(dealt * 0.15f));
			applyDamage(attacker, reflected);
			if (verbose) {
				say(FishFight.tr("battle.spiky", attacker.name, reflected).withStyle(ChatFormatting.DARK_GREEN));
			}
		}
		if (attacker.stats.has(Mutation.ELECTRIC) && defender.alive() && !defender.stunned && random.nextFloat() < 0.2f) {
			defender.stunned = true;
			say(FishFight.tr("battle.paralyze", defender.name).withStyle(ChatFormatting.YELLOW));
			particles(defender, ParticleTypes.ELECTRIC_SPARK, 25);
		}
		return dealt;
	}

	private void applyDamage(Fighter f, int amount) {
		f.hp -= amount;
		if (f.hp <= 0 && f.stats.species() == FishSpecies.COELACANTH && !f.fossilUsed) {
			// 4億年生き延びた執念で1回だけ耐える
			f.hp = 1;
			f.fossilUsed = true;
			say(FishFight.tr("battle.fossil", f.name).withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD));
		}
		f.hp = Math.max(0, f.hp);
		updateName(f);
	}

	private void sproutArms(Fighter f) {
		f.arms = true;
		f.sproutedArms = true;
		f.atkMul *= 1.4f;
		f.entity.sproutArms();
		say(FishFight.tr("battle.sprout_arms", f.name).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
		titleAll(Component.literal("！？").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
				FishFight.tr("battle.sprout_arms_title", f.stats.species().displayName()).withStyle(ChatFormatting.YELLOW), 30);
		playSound(SoundEvents.ANVIL_LAND, 0.8f, 1.6f);
		playSound(SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 0.6f, 1.4f);
		particles(f, ParticleTypes.POOF, 25);
		particles(f, ParticleTypes.EXPLOSION, 1);
		cooldown += 15;
	}

	private void sproutLegs(Fighter f) {
		f.legs = true;
		f.sproutedLegs = true;
		f.spdMul *= 1.3f;
		f.entity.sproutLegs();
		say(FishFight.tr("battle.sprout_legs", f.name).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
		playSound(SoundEvents.SLIME_SQUISH, 1.0f, 0.6f);
		particles(f, ParticleTypes.POOF, 20);
	}

	// ---- 決着 ----

	private void finish(Fighter winner) {
		if (state != State.FIGHT) {
			return;
		}
		state = State.ENDING;
		timer = 0;
		this.winner = winner;
		Fighter loser = other(winner);
		loser.entity.setDefeated();
		updateBars();
		playSound(SoundEvents.COD_FLOP, 1.0f, 0.8f);
		say(FishFight.tr("battle.defeated", loser.name).withStyle(ChatFormatting.GRAY));
		say(FishFight.tr("battle.winner", winner.name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		particles(winner, ParticleTypes.TOTEM_OF_UNDYING, 50);
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
			say(FishFight.tr("battle.cancelled").withStyle(ChatFormatting.GRAY));
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

	private void announce(Fighter actor, String move) {
		say(FishFight.tr("battle.uses", actor.name, FishFight.tr("move." + move).withStyle(ChatFormatting.AQUA)));
	}

	private void updateName(Fighter f) {
		ChatFormatting hpColor = f.hpRatio() > 0.5f ? ChatFormatting.GREEN : f.hpRatio() > 0.2f ? ChatFormatting.YELLOW : ChatFormatting.RED;
		MutableComponent name = Component.empty()
				.append(f.stats.species().displayName().withStyle(f.stats.rarity().color))
				.append(Component.literal(" ❤ " + f.hp + "/" + f.maxHp).withStyle(hpColor));
		f.entity.setCustomName(name);
	}

	private void updateBars() {
		for (int i = 0; i < 2; i++) {
			bars[i].setProgress(fighters[i].hpRatio());
		}
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

	private void say(Component message) {
		for (ServerPlayer p : viewers()) {
			p.sendSystemMessage(message);
		}
	}

	private void titleAll(Component title, Component subtitle, int stay) {
		for (ServerPlayer p : viewers()) {
			title(p, title, subtitle, stay);
		}
	}

	private static void title(ServerPlayer player, Component title, Component subtitle, int stay) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(3, stay, 8));
		player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		player.connection.send(new ClientboundSetTitleTextPacket(title));
	}

	private void particles(Fighter f, ParticleOptions type, int count) {
		Vec3 p = f.entity.position();
		level.sendParticles(type, p.x, p.y + 0.5, p.z, count, 0.4, 0.4, 0.4, 0.1);
	}

	private void playSound(SoundEvent sound, float volume, float pitch) {
		level.playSound(null, center.x, center.y, center.z, sound, SoundSource.PLAYERS, volume, pitch);
	}
}
