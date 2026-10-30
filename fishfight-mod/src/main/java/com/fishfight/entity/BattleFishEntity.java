package com.fishfight.entity;

import com.fishfight.FishFight;
import com.fishfight.battle.FishBattle;
import com.fishfight.battle.Fighter;
import com.fishfight.fish.FishRarity;
import com.fishfight.fish.FishSpecies;
import com.fishfight.fish.FishStats;
import com.fishfight.fish.Mutation;
import com.fishfight.fish.Skill;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * バトル用の魚モブ。相手を自分で追いかけて攻撃し、条件を満たすとスキルが自動で発動する。
 * <ul>
 *   <li>陸で脚がない → ピチピチ跳ねながら近づく（攻撃が当たりにくい）</li>
 *   <li>脚がある → 普通に歩いて戦う</li>
 *   <li>水中 → すいすい泳ぐ</li>
 * </ul>
 */
public class BattleFishEntity extends PathfinderMob {
	public static final byte EVENT_ATTACK = 100;
	public static final byte EVENT_SPROUT_ARMS = 102;
	public static final byte EVENT_SPROUT_LEGS = 103;

	private static final EntityDataAccessor<String> SPECIES = SynchedEntityData.defineId(BattleFishEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Integer> RARITY = SynchedEntityData.defineId(BattleFishEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> ARMS = SynchedEntityData.defineId(BattleFishEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> LEGS = SynchedEntityData.defineId(BattleFishEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> GOLDEN = SynchedEntityData.defineId(BattleFishEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> PUFFED = SynchedEntityData.defineId(BattleFishEntity.class, EntityDataSerializers.BOOLEAN);

	/** 脚生えが発動するまでに跳ねる時間 */
	private static final int FLOP_TICKS_FOR_LEGS = 60;
	/** 闘技場の半径。これより外に出たら中心へ戻ろうとする */
	private static final double ARENA_RADIUS = 6.0;
	/** バトルの中心からこれ以上離れたら引き戻す */
	private static final double LEASH = 14.0;

	// ---- クライアント側のアニメーション ----
	public int attackAnim;
	public int armsSproutAnim;
	public int legsSproutAnim;
	public boolean punchLeft;

	// ---- サーバー側の状態 ----
	@Nullable
	private FishBattle battle;
	@Nullable
	private Fighter fighter;
	@Nullable
	private BattleFishEntity opponent;
	private boolean active;

	private float atkMul = 1f;
	private float spdMul = 1f;
	private float cooldownMul = 1f;
	private int attackCooldown = 20;
	private int pendingPunches;
	private int punchDelay;
	private int stunTicks;
	private int flopTicks;
	private int hopCooldown;
	private int chargeTicks;
	private int puffTicks;
	private int dischargeCooldown = 60;
	private int chargeCooldown = 40;
	private int waterGunCooldown = 50;
	private int puffCooldown;
	private final Set<Skill> usedSkills = EnumSet.noneOf(Skill.class);

	public BattleFishEntity(EntityType<? extends BattleFishEntity> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.ATTACK_DAMAGE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(SPECIES, FishSpecies.CARP.id);
		builder.define(RARITY, 0);
		builder.define(ARMS, false);
		builder.define(LEGS, false);
		builder.define(GOLDEN, false);
		builder.define(PUFFED, false);
	}

	/** バトル開始前にサーバー側で呼ぶ */
	public void setup(FishBattle battle, Fighter fighter) {
		this.battle = battle;
		this.fighter = fighter;
		FishStats stats = fighter.stats;
		this.entityData.set(SPECIES, stats.species().id);
		this.entityData.set(RARITY, stats.rarity().ordinal());
		this.entityData.set(ARMS, stats.has(Mutation.ARMS));
		this.entityData.set(LEGS, stats.has(Mutation.LEGS));
		this.entityData.set(GOLDEN, stats.has(Mutation.GOLDEN));
		this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(stats.hp());
		this.setHealth(stats.hp());
		this.getAttribute(Attributes.SCALE).setBaseValue(stats.renderScale());
		this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.18 + stats.spd() * 0.004);
		this.setCustomNameVisible(true);
		updateName();
	}

	public void setOpponent(BattleFishEntity opponent) {
		this.opponent = opponent;
	}

	public void setActive(boolean active) {
		this.active = active;
		if (!active) {
			this.getNavigation().stop();
		}
	}

	// ---- 見た目用のゲッター ----

	public FishSpecies getSpecies() {
		return FishSpecies.byId(this.entityData.get(SPECIES));
	}

	public FishRarity getRarity() {
		return FishRarity.byOrdinal(this.entityData.get(RARITY));
	}

	public boolean hasArms() {
		return this.entityData.get(ARMS);
	}

	public boolean hasLegs() {
		return this.entityData.get(LEGS);
	}

	public boolean isGolden() {
		return this.entityData.get(GOLDEN);
	}

	public boolean isPuffed() {
		return this.entityData.get(PUFFED);
	}

	/** 陸で脚がない＝跳ねている */
	public boolean isFlopping() {
		return !hasLegs() && !isInWater() && isAlive();
	}

	@Nullable
	public Fighter getFighter() {
		return fighter;
	}

	// ---- AI ----

	@Override
	protected void registerGoals() {
		// ゴールは使わず customServerAiStep で全部決める
	}

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (!active || fighter == null || opponent == null || !opponent.isAlive() || !isAlive()) {
			return;
		}
		tickCooldowns();
		if (battle != null && position().distanceTo(battle.center()) > LEASH) {
			Vec3 c = battle.center();
			teleportTo(c.x + random.nextGaussian(), c.y, c.z + random.nextGaussian());
		}
		if (stunTicks > 0) {
			stunTicks--;
			getNavigation().stop();
			return;
		}
		double dist = distanceTo(opponent);
		triggerSkills(dist);
		move(dist);
		attack(dist);
	}

	private void tickCooldowns() {
		if (dischargeCooldown > 0) dischargeCooldown--;
		if (chargeCooldown > 0) chargeCooldown--;
		if (waterGunCooldown > 0) waterGunCooldown--;
		if (puffCooldown > 0) puffCooldown--;
		if (puffTicks > 0 && --puffTicks == 0) {
			this.entityData.set(PUFFED, false);
		}
	}

	private void move(double dist) {
		// 闘技場から出そうなら、相手ではなく中心へ向かう
		boolean outside = battle != null && position().distanceTo(battle.center()) > ARENA_RADIUS;
		Vec3 goal = outside ? battle.center() : opponent.position();
		Vec3 toTarget = goal.subtract(position());
		Vec3 flat = new Vec3(toTarget.x, 0, toTarget.z);
		Vec3 dir = flat.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : flat.normalize();
		float speedFactor = 1f + spd() / 40f;

		if (chargeTicks > 0) {
			chargeTicks--;
			face(dir);
			if (dist <= reach() + 0.3) {
				chargeTicks = 0;
				announce(Skill.CHARGE, Component.translatable("fishfight.battle.skill.charge_hit", fighter.name));
				strike(1.5f, 0.95f, 10, false);
				opponent.knockback(1.0, -dir.x, -dir.z);
			}
			return;
		}

		if (isInWater()) {
			// 水中：まっすぐ泳いで近づく
			getNavigation().stop();
			face(dir);
			if (outside || dist > reach() * 0.8) {
				Vec3 swim = toTarget.normalize().scale(0.045 * speedFactor);
				setDeltaMovement(getDeltaMovement().scale(0.85).add(swim).add(0, 0.005, 0));
			}
			return;
		}

		if (hasLegs()) {
			// 脚がある：歩いて近づく
			if (outside) {
				getNavigation().moveTo(goal.x, goal.y, goal.z, 1.0);
			} else if (dist > reach() * 0.8) {
				getNavigation().moveTo(opponent, 1.0);
			} else {
				getNavigation().stop();
				face(dir);
			}
			return;
		}

		// 脚がない：その場でピチピチ跳ねながら、だいたい相手の方へ
		getNavigation().stop();
		flopTicks++;
		if (onGround() && --hopCooldown <= 0) {
			double angle = (random.nextDouble() - 0.5) * (dist < reach() && !outside ? 3.0 : 1.6);
			Vec3 hop = dir.yRot((float) angle);
			double h = 0.16 + random.nextDouble() * 0.14;
			setDeltaMovement(hop.x * h * speedFactor, 0.3 + random.nextDouble() * 0.15, hop.z * h * speedFactor);
			this.hasImpulse = true;
			hopCooldown = 5 + random.nextInt(10);
			face(hop);
			playSound(SoundEvents.COD_FLOP, 0.5f, 0.9f + random.nextFloat() * 0.3f);
		}
	}

	private void face(Vec3 dir) {
		float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90f;
		setYRot(yaw);
		this.yBodyRot = yaw;
		this.yHeadRot = yaw;
	}

	private double reach() {
		return (getBbWidth() + opponent.getBbWidth()) * 0.5 + 0.7;
	}

	private void attack(double dist) {
		if (pendingPunches > 0) {
			if (--punchDelay <= 0) {
				pendingPunches--;
				punchDelay = 4;
				if (dist <= reach() + 0.5) {
					strike(0.5f, 0.95f, 0, true);
				}
			}
			return;
		}
		if (attackCooldown > 0) {
			attackCooldown--;
			return;
		}
		if (dist > reach()) {
			return;
		}
		if (hasArms() && random.nextFloat() < 0.6f) {
			// 腕があるとパンチ連打
			pendingPunches = 2 + random.nextInt(2);
			punchDelay = 0;
		} else {
			// 跳ねながらだと狙いが定まらない
			strike(1.0f, isFlopping() ? 0.55f : 0.92f, 0, false);
		}
		attackCooldown = Math.round(Math.max(12f, 45f - spd() * 0.7f) * cooldownMul * (isFlopping() ? 1.3f : 1f));
	}

	/** 1回分の攻撃。命中したら与えたダメージを返す */
	private float strike(float power, float accuracy, int extraCrit, boolean punch) {
		this.level().broadcastEntityEvent(this, EVENT_ATTACK);
		if (random.nextFloat() > accuracy) {
			playSound(SoundEvents.PLAYER_ATTACK_NODAMAGE, 0.8f, 1.2f);
			return 0f;
		}
		float as = spd();
		float ds = opponent.spd();
		float dodge = 0.05f + Math.max(0f, (ds - as) / Math.max(1f, ds + as)) * 0.3f;
		if (opponent.fighter != null && opponent.fighter.stats.has(Mutation.TINY)) {
			dodge += 0.08f;
		}
		if (random.nextFloat() < Math.min(0.35f, dodge)) {
			particles(opponent, ParticleTypes.CLOUD, 6);
			playSound(SoundEvents.PLAYER_ATTACK_NODAMAGE, 0.8f, 1.4f);
			return 0f;
		}
		float damage = atk() * power * (0.85f + random.nextFloat() * 0.3f);
		damage *= 100f / (100f + opponent.def() * 2.5f);
		boolean crit = random.nextFloat() * 100f < fighter.stats.crit() + extraCrit;
		if (crit) {
			damage *= 1.75f;
			particles(opponent, ParticleTypes.CRIT, 15);
			playSound(SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 1.0f);
		} else {
			playSound(punch ? SoundEvents.PLAYER_ATTACK_KNOCKBACK : SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 1.1f);
		}
		float dealt = opponent.takeFishDamage(this, Math.max(1f, damage));
		if (fighter.stats.has(Mutation.ELECTRIC) && opponent.isAlive() && random.nextFloat() < 0.15f) {
			opponent.stun(30);
			announce(null, Component.translatable("fishfight.battle.paralyze", opponent.fighter.name));
		}
		return dealt;
	}

	/** 相手の魚からの攻撃を受ける。ふくらむ・根性・トゲはここで処理 */
	private float takeFishDamage(BattleFishEntity attacker, float amount) {
		if (fighter != null) {
			if (fighter.stats.has(Skill.PUFF_UP) && puffCooldown <= 0 && getHealth() < getMaxHealth() * 0.8f) {
				puffTicks = 100;
				puffCooldown = 220;
				this.entityData.set(PUFFED, true);
				amount *= 0.5f;
				announce(Skill.PUFF_UP, Component.translatable("fishfight.battle.skill.puff_up", fighter.name));
				playSound(SoundEvents.PUFFER_FISH_BLOW_UP, 1.0f, 1.0f);
			}
			if (amount >= getHealth() && fighter.stats.has(Skill.ENDURE) && usedSkills.add(Skill.ENDURE)) {
				amount = Math.max(0f, getHealth() - 1f);
				announce(Skill.ENDURE, Component.translatable("fishfight.battle.skill.endure", fighter.name));
				particles(this, ParticleTypes.TOTEM_OF_UNDYING, 30);
			}
			if (fighter.stats.has(Mutation.SPIKY) && attacker.isAlive()) {
				attacker.invulnerableTime = 0;
				attacker.hurt(damageSources().thorns(this), Math.max(1f, amount * 0.15f));
			}
		}
		this.invulnerableTime = 0;
		float before = getHealth();
		hurt(damageSources().mobAttack(attacker), amount);
		particles(this, ParticleTypes.DAMAGE_INDICATOR, Math.min(10, 1 + (int) (amount / 4)));
		return before - getHealth();
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		// バトル相手の攻撃と /kill 以外は受け付けない（プレイヤーが殴っても効かない）
		boolean fromOpponent = opponent != null && (source.getEntity() == opponent || source.getDirectEntity() == opponent);
		if (!fromOpponent && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		boolean result = super.hurt(source, amount);
		updateName();
		return result;
	}

	public void stun(int ticks) {
		stunTicks = Math.max(stunTicks, ticks);
		particles(this, ParticleTypes.ELECTRIC_SPARK, 20);
	}

	// ---- スキル ----

	private void triggerSkills(double dist) {
		FishStats stats = fighter.stats;
		float ratio = getHealth() / getMaxHealth();

		if (stats.has(Skill.ARM_SPROUT) && !hasArms() && ratio <= 0.5f) {
			sproutArms();
		}
		if (stats.has(Skill.LEG_SPROUT) && !hasLegs() && flopTicks >= FLOP_TICKS_FOR_LEGS) {
			sproutLegs();
		}
		if (stats.has(Skill.GIANTIZE) && ratio <= 0.3f && usedSkills.add(Skill.GIANTIZE)) {
			getAttribute(Attributes.SCALE).setBaseValue(getAttributeValue(Attributes.SCALE) * 1.4);
			atkMul *= 1.3f;
			announce(Skill.GIANTIZE, Component.translatable("fishfight.battle.skill.giantize", fighter.name));
			particles(this, ParticleTypes.EXPLOSION, 2);
			playSound(SoundEvents.RAVAGER_ROAR, 0.8f, 1.5f);
		}
		if (stats.has(Skill.REGENERATE) && ratio <= 0.35f && usedSkills.add(Skill.REGENERATE)) {
			float heal = getMaxHealth() * 0.35f;
			heal(heal);
			updateName();
			announce(Skill.REGENERATE, Component.translatable("fishfight.battle.skill.regenerate", fighter.name, Math.round(heal)));
			particles(this, ParticleTypes.HEART, 8);
			playSound(SoundEvents.PLAYER_LEVELUP, 0.6f, 1.6f);
		}
		if (stats.has(Skill.FRENZY) && ratio <= 0.4f && usedSkills.add(Skill.FRENZY)) {
			cooldownMul *= 0.6f;
			spdMul *= 1.3f;
			announce(Skill.FRENZY, Component.translatable("fishfight.battle.skill.frenzy", fighter.name));
			particles(this, ParticleTypes.ANGRY_VILLAGER, 8);
		}
		if (stats.has(Skill.DISCHARGE) && dischargeCooldown <= 0 && dist <= 4.0) {
			dischargeCooldown = 160;
			announce(Skill.DISCHARGE, Component.translatable("fishfight.battle.skill.discharge", fighter.name, opponent.fighter.name));
			opponent.takeFishDamage(this, Math.max(1f, atk() * 0.5f));
			opponent.stun(40);
			particles(this, ParticleTypes.ELECTRIC_SPARK, 40);
			playSound(SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6f, 1.6f);
		}
		if (stats.has(Skill.CHARGE) && chargeCooldown <= 0 && dist >= 4.0 && (onGround() || isInWater())) {
			chargeCooldown = 140;
			chargeTicks = 20;
			Vec3 dir = opponent.position().subtract(position()).normalize();
			setDeltaMovement(dir.x * 0.9, 0.25, dir.z * 0.9);
			this.hasImpulse = true;
			announce(Skill.CHARGE, Component.translatable("fishfight.battle.skill.charge", fighter.name));
			playSound(SoundEvents.RAVAGER_STEP, 1.0f, 1.2f);
		}
		if (stats.has(Skill.WATER_GUN) && waterGunCooldown <= 0 && dist >= 3.0 && dist <= 12.0) {
			waterGunCooldown = 100;
			waterGun();
		}
	}

	private void sproutArms() {
		this.entityData.set(ARMS, true);
		this.level().broadcastEntityEvent(this, EVENT_SPROUT_ARMS);
		atkMul *= 1.5f;
		announce(Skill.ARM_SPROUT, Component.translatable("fishfight.battle.skill.arm_sprout", fighter.name));
		if (battle != null) {
			battle.titleAll(Component.literal("！？").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
					Component.translatable("fishfight.battle.sprout_arms_title", fighter.stats.species().displayName())
							.withStyle(ChatFormatting.YELLOW), 30);
		}
		playSound(SoundEvents.ANVIL_LAND, 0.7f, 1.6f);
		playSound(SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 0.5f, 1.4f);
		particles(this, ParticleTypes.POOF, 25);
		stunTicks = Math.max(stunTicks, 10);
	}

	private void sproutLegs() {
		this.entityData.set(LEGS, true);
		this.level().broadcastEntityEvent(this, EVENT_SPROUT_LEGS);
		spdMul *= 1.2f;
		getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(getAttributeBaseValue(Attributes.MOVEMENT_SPEED) * 1.2);
		announce(Skill.LEG_SPROUT, Component.translatable("fishfight.battle.skill.leg_sprout", fighter.name));
		playSound(SoundEvents.SLIME_SQUISH, 1.0f, 0.6f);
		particles(this, ParticleTypes.POOF, 20);
		stunTicks = Math.max(stunTicks, 10);
	}

	private void waterGun() {
		announce(Skill.WATER_GUN, Component.translatable("fishfight.battle.skill.water_gun", fighter.name));
		this.level().broadcastEntityEvent(this, EVENT_ATTACK);
		playSound(SoundEvents.BUCKET_EMPTY, 1.0f, 1.4f);
		if (level() instanceof ServerLevel server) {
			Vec3 from = position().add(0, getBbHeight() * 0.5, 0);
			Vec3 to = opponent.position().add(0, opponent.getBbHeight() * 0.5, 0);
			for (int i = 0; i <= 12; i++) {
				Vec3 p = from.lerp(to, i / 12.0);
				server.sendParticles(ParticleTypes.SPLASH, p.x, p.y, p.z, 4, 0.05, 0.05, 0.05, 0.0);
			}
		}
		if (random.nextFloat() < 0.8f) {
			opponent.takeFishDamage(this, Math.max(1f, atk() * 0.7f * 100f / (100f + opponent.def() * 2.5f)));
		}
	}

	// ---- ステータス ----

	private float atk() {
		return fighter == null ? 1f : fighter.stats.atk() * atkMul;
	}

	private float def() {
		return fighter == null ? 0f : fighter.stats.def() * (puffTicks > 0 ? 2f : 1f);
	}

	private float spd() {
		return fighter == null ? 10f : fighter.stats.spd() * spdMul;
	}

	public void updateName() {
		if (fighter == null) {
			return;
		}
		float ratio = getHealth() / getMaxHealth();
		ChatFormatting hpColor = ratio > 0.5f ? ChatFormatting.GREEN : ratio > 0.2f ? ChatFormatting.YELLOW : ChatFormatting.RED;
		setCustomName(Component.empty()
				.append(fighter.stats.species().displayName().withStyle(fighter.stats.rarity().color))
				.append(Component.literal(" ❤ " + Mth.ceil(getHealth()) + "/" + Mth.ceil(getMaxHealth())).withStyle(hpColor)));
	}

	private void announce(@Nullable Skill skill, Component message) {
		if (battle != null) {
			battle.announce(skill == null ? message
					: Component.literal("【").withStyle(skill.color).append(skill.displayName()).append("】").append(message));
		}
		if (skill != null) {
			FishFight.LOGGER.debug("skill {} used by {}", skill.id, fighter == null ? "?" : fighter.stats.species().id);
		}
	}

	private void particles(BattleFishEntity target, ParticleOptions type, int count) {
		if (level() instanceof ServerLevel server) {
			server.sendParticles(type, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
					count, 0.3, 0.3, 0.3, 0.1);
		}
	}

	// ---- バニラ挙動の調整 ----

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide) {
			if (attackAnim > 0) attackAnim--;
			if (armsSproutAnim > 0) armsSproutAnim--;
			if (legsSproutAnim > 0) legsSproutAnim--;
		} else if (battle == null && this.tickCount > 5) {
			// バトルに属していない（ワールド読み込みで復活した等）なら消す
			this.discard();
		}
	}

	@Override
	public void handleEntityEvent(byte id) {
		switch (id) {
			case EVENT_ATTACK -> {
				attackAnim = 10;
				punchLeft = !punchLeft;
			}
			case EVENT_SPROUT_ARMS -> armsSproutAnim = 20;
			case EVENT_SPROUT_LEGS -> legsSproutAnim = 20;
			default -> super.handleEntityEvent(id);
		}
	}

	@Override
	public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.COD_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.COD_DEATH;
	}
}
