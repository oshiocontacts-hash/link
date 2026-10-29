package com.fishfight.fish;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringRepresentable;

/**
 * 特異体質。釣れた時点でまれに付いている。腕付きの魚はバトルで殴る。
 */
public enum Mutation implements StringRepresentable {
	//   id          確率    hp     atk    def    spd
	ARMS("arms", 0.07f, 1.0f, 1.35f, 1.0f, 1.0f, ChatFormatting.YELLOW),
	LEGS("legs", 0.045f, 1.0f, 1.0f, 1.0f, 1.3f, ChatFormatting.YELLOW),
	GIANT("giant", 0.03f, 1.5f, 1.0f, 1.15f, 0.85f, ChatFormatting.DARK_RED),
	TINY("tiny", 0.03f, 0.75f, 1.0f, 1.0f, 1.35f, ChatFormatting.DARK_AQUA),
	GOLDEN("golden", 0.015f, 1.25f, 1.25f, 1.25f, 1.25f, ChatFormatting.GOLD),
	MUSCLE("muscle", 0.04f, 1.0f, 1.2f, 1.2f, 1.0f, ChatFormatting.RED),
	ELECTRIC("electric", 0.035f, 1.0f, 1.0f, 1.0f, 1.05f, ChatFormatting.YELLOW),
	SPIKY("spiky", 0.04f, 1.0f, 1.0f, 1.1f, 1.0f, ChatFormatting.DARK_GREEN);

	public static final Codec<Mutation> CODEC = StringRepresentable.fromEnum(Mutation::values);

	public final String id;
	public final float chance;
	public final float hpMul;
	public final float atkMul;
	public final float defMul;
	public final float spdMul;
	public final ChatFormatting color;

	Mutation(String id, float chance, float hpMul, float atkMul, float defMul, float spdMul, ChatFormatting color) {
		this.id = id;
		this.chance = chance;
		this.hpMul = hpMul;
		this.atkMul = atkMul;
		this.defMul = defMul;
		this.spdMul = spdMul;
		this.color = color;
	}

	@Override
	public String getSerializedName() {
		return id;
	}

	public MutableComponent displayName() {
		return Component.translatable("fishfight.mutation." + id).withStyle(color);
	}
}
