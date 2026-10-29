package com.fishfight.fish;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringRepresentable;

/**
 * 性格。ステータスに少しだけ補正がかかる。
 */
public enum Personality implements StringRepresentable {
	//      id         hp     atk    def    spd   会心+
	BRAVE("brave", 1.0f, 1.1f, 1.0f, 0.9f, 0),
	TIMID("timid", 1.0f, 0.9f, 1.0f, 1.15f, 0),
	RELAXED("relaxed", 1.15f, 1.0f, 1.0f, 0.9f, 0),
	BOLD("bold", 1.0f, 0.95f, 1.15f, 1.0f, 0),
	HASTY("hasty", 1.0f, 1.0f, 0.9f, 1.1f, 0),
	QUIRKY("quirky", 1.0f, 1.0f, 1.0f, 1.0f, 8),
	SERIOUS("serious", 1.0f, 1.0f, 1.0f, 1.0f, 0);

	public static final Codec<Personality> CODEC = StringRepresentable.fromEnum(Personality::values);

	public final String id;
	public final float hpMul;
	public final float atkMul;
	public final float defMul;
	public final float spdMul;
	public final int critBonus;

	Personality(String id, float hpMul, float atkMul, float defMul, float spdMul, int critBonus) {
		this.id = id;
		this.hpMul = hpMul;
		this.atkMul = atkMul;
		this.defMul = defMul;
		this.spdMul = spdMul;
		this.critBonus = critBonus;
	}

	@Override
	public String getSerializedName() {
		return id;
	}

	public MutableComponent displayName() {
		return Component.translatable("fishfight.personality." + id);
	}
}
