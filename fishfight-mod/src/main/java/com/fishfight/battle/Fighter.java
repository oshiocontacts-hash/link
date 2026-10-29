package com.fishfight.battle;

import com.fishfight.entity.BattleFishEntity;
import com.fishfight.fish.FishStats;
import com.fishfight.fish.Mutation;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * バトル中の魚1匹分の状態。元の個体データは変更しない。
 */
public class Fighter {
	public final FishStats stats;
	/** 持ち主のプレイヤー。野生の魚なら null */
	@Nullable
	public final UUID owner;
	public final Component name;
	public BattleFishEntity entity;

	public final int maxHp;
	public int hp;
	public float atkMul = 1f;
	public float defMul = 1f;
	public float spdMul = 1f;
	public int gauge;

	public boolean arms;
	public boolean legs;
	public boolean sproutedArms;
	public boolean sproutedLegs;
	public boolean dodgeNext;
	public boolean stunned;
	public boolean fossilUsed;
	public int puffs;

	public Fighter(FishStats stats, @Nullable UUID owner, Component name) {
		this.stats = stats;
		this.owner = owner;
		this.name = name;
		this.maxHp = stats.hp();
		this.hp = stats.hp();
		this.arms = stats.has(Mutation.ARMS);
		this.legs = stats.has(Mutation.LEGS);
	}

	public float atk() {
		return stats.atk() * atkMul;
	}

	public float def() {
		return stats.def() * defMul;
	}

	public float spd() {
		return stats.spd() * spdMul;
	}

	public float hpRatio() {
		return Math.max(0f, hp / (float) maxHp);
	}

	public boolean alive() {
		return hp > 0;
	}
}
