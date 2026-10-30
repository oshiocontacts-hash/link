package com.fishfight.battle;

import com.fishfight.entity.BattleFishEntity;
import com.fishfight.fish.FishStats;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * バトルに出る魚1匹の情報。戦闘中の状態はモブ（{@link BattleFishEntity}）が自分で持つ。
 */
public class Fighter {
	public final FishStats stats;
	/** 持ち主のプレイヤー。野生の魚なら null */
	@Nullable
	public final UUID owner;
	public final Component name;
	public BattleFishEntity entity;

	public Fighter(FishStats stats, @Nullable UUID owner, Component name) {
		this.stats = stats;
		this.owner = owner;
		this.name = name;
	}

	public float hpRatio() {
		return entity == null ? 0f : Math.max(0f, entity.getHealth() / entity.getMaxHealth());
	}

	public boolean defeated() {
		return entity == null || entity.isRemoved() || entity.isDeadOrDying();
	}
}
