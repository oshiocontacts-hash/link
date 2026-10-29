package com.fishfight.entity;

import com.fishfight.fish.FishRarity;
import com.fishfight.fish.FishSpecies;
import com.fishfight.fish.FishStats;
import com.fishfight.fish.Mutation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * バトル中だけ出現する魚。動きは {@link com.fishfight.battle.FishBattle} が全部決める。
 */
public class BattleFishEntity extends Entity {
	public static final byte EVENT_ATTACK = 60;
	public static final byte EVENT_HURT = 61;
	public static final byte EVENT_SPROUT_ARMS = 62;
	public static final byte EVENT_SPROUT_LEGS = 63;

	private static final EntityDataAccessor<String> SPECIES = SynchedEntityData.defineId(BattleFishEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Integer> RARITY = SynchedEntityData.defineId(BattleFishEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(BattleFishEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Boolean> ARMS = SynchedEntityData.defineId(BattleFishEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> LEGS = SynchedEntityData.defineId(BattleFishEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> GOLDEN = SynchedEntityData.defineId(BattleFishEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DEFEATED = SynchedEntityData.defineId(BattleFishEntity.class, EntityDataSerializers.BOOLEAN);

	/** バトルに管理されているか。ワールド読み込みで復活した魚は消す */
	private boolean managed;

	// クライアント側のアニメーション用タイマー
	public int attackAnim;
	public int hurtAnim;
	public int armsSproutAnim;
	public int legsSproutAnim;
	public int defeatedTicks;
	public boolean punchLeft;

	public BattleFishEntity(EntityType<? extends BattleFishEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(SPECIES, FishSpecies.CARP.id);
		builder.define(RARITY, 0);
		builder.define(SCALE, 1.0f);
		builder.define(ARMS, false);
		builder.define(LEGS, false);
		builder.define(GOLDEN, false);
		builder.define(DEFEATED, false);
	}

	public void setup(FishStats stats) {
		this.managed = true;
		this.entityData.set(SPECIES, stats.species().id);
		this.entityData.set(RARITY, stats.rarity().ordinal());
		this.entityData.set(SCALE, stats.renderScale());
		this.entityData.set(ARMS, stats.has(Mutation.ARMS));
		this.entityData.set(LEGS, stats.has(Mutation.LEGS));
		this.entityData.set(GOLDEN, stats.has(Mutation.GOLDEN));
	}

	public FishSpecies getSpecies() {
		return FishSpecies.byId(this.entityData.get(SPECIES));
	}

	public FishRarity getRarity() {
		return FishRarity.byOrdinal(this.entityData.get(RARITY));
	}

	public float getFishScale() {
		return this.entityData.get(SCALE);
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

	public boolean isDefeated() {
		return this.entityData.get(DEFEATED);
	}

	public void sproutArms() {
		this.entityData.set(ARMS, true);
		this.level().broadcastEntityEvent(this, EVENT_SPROUT_ARMS);
	}

	public void sproutLegs() {
		this.entityData.set(LEGS, true);
		this.level().broadcastEntityEvent(this, EVENT_SPROUT_LEGS);
	}

	public void setDefeated() {
		this.entityData.set(DEFEATED, true);
	}

	public void playAttack() {
		this.level().broadcastEntityEvent(this, EVENT_ATTACK);
	}

	public void playHurt() {
		this.level().broadcastEntityEvent(this, EVENT_HURT);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide) {
			if (attackAnim > 0) attackAnim--;
			if (hurtAnim > 0) hurtAnim--;
			if (armsSproutAnim > 0) armsSproutAnim--;
			if (legsSproutAnim > 0) legsSproutAnim--;
			if (isDefeated()) defeatedTicks++;
		} else if (!managed && this.tickCount > 5) {
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
			case EVENT_HURT -> hurtAnim = 10;
			case EVENT_SPROUT_ARMS -> armsSproutAnim = 20;
			case EVENT_SPROUT_LEGS -> legsSproutAnim = 20;
			default -> super.handleEntityEvent(id);
		}
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
		return new ClientboundAddEntityPacket(this, serverEntity);
	}
}
