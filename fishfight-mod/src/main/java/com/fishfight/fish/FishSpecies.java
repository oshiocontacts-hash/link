package com.fishfight.fish;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

/**
 * 魚の種類。基礎ステータス・生息地・釣りやすさ・見た目の比率を持つ。
 */
public enum FishSpecies implements StringRepresentable {
	//        id            habitat         weight color     cm min/max  kg係数 hp  atk def spd crit 難易度 表示倍率  体の比率(幅,高さ,長さ)
	CARP("carp", Habitat.FRESH, 30f, 0xD9822B, 30, 90, 12f, 80, 14, 14, 10, 5, 0.9f, 0.8f, 1.0f, 1.0f, 1.1f),
	MACKEREL("mackerel", Habitat.OCEAN, 30f, 0x4A7FB0, 25, 50, 10f, 60, 13, 9, 20, 8, 0.8f, 0.65f, 0.8f, 0.8f, 1.1f),
	PUFFERFISH("pufferfish", Habitat.OCEAN, 18f, 0xE8D35A, 15, 45, 25f, 85, 13, 22, 8, 5, 1.0f, 0.7f, 1.5f, 1.4f, 0.85f),
	PIRANHA("piranha", Habitat.FRESH, 16f, 0xB54646, 15, 40, 22f, 50, 22, 8, 18, 12, 1.2f, 0.6f, 0.9f, 1.15f, 0.8f),
	CATFISH("catfish", Habitat.FRESH, 14f, 0x6B5B45, 50, 150, 12f, 100, 16, 16, 7, 5, 1.1f, 0.95f, 1.25f, 0.8f, 1.4f),
	TUNA("tuna", Habitat.OCEAN, 12f, 0x2E3F66, 100, 300, 50f, 110, 18, 14, 16, 6, 1.4f, 1.1f, 1.3f, 1.2f, 1.5f),
	MARLIN("marlin", Habitat.OCEAN, 6f, 0x3570A8, 150, 450, 15f, 95, 24, 11, 22, 10, 1.7f, 1.15f, 0.9f, 1.1f, 1.9f),
	SHARK("shark", Habitat.OCEAN, 4f, 0x7D8A94, 150, 600, 12f, 130, 26, 15, 14, 8, 1.9f, 1.3f, 1.3f, 1.2f, 2.0f),
	OARFISH("oarfish", Habitat.OCEAN, 1.5f, 0xC8D0E0, 300, 800, 2f, 120, 20, 18, 18, 10, 2.2f, 1.1f, 0.55f, 1.3f, 3.0f),
	COELACANTH("coelacanth", Habitat.ANY, 0.8f, 0x3C4E7A, 100, 200, 17f, 140, 22, 22, 12, 8, 2.4f, 1.1f, 1.3f, 1.2f, 1.5f);

	public static final Codec<FishSpecies> CODEC = StringRepresentable.fromEnum(FishSpecies::values);

	public final String id;
	public final Habitat habitat;
	public final float catchWeight;
	public final int color;
	public final int minCm;
	public final int maxCm;
	/** 重さ(kg) = 係数 × 体長(m)^3 */
	public final float density;
	public final int baseHp;
	public final int baseAtk;
	public final int baseDef;
	public final int baseSpd;
	public final int baseCrit;
	public final float difficulty;
	public final float renderScale;
	public final float widthScale;
	public final float heightScale;
	public final float lengthScale;

	FishSpecies(String id, Habitat habitat, float catchWeight, int color, int minCm, int maxCm, float density,
			int baseHp, int baseAtk, int baseDef, int baseSpd, int baseCrit, float difficulty,
			float renderScale, float widthScale, float heightScale, float lengthScale) {
		this.id = id;
		this.habitat = habitat;
		this.catchWeight = catchWeight;
		this.color = color;
		this.minCm = minCm;
		this.maxCm = maxCm;
		this.density = density;
		this.baseHp = baseHp;
		this.baseAtk = baseAtk;
		this.baseDef = baseDef;
		this.baseSpd = baseSpd;
		this.baseCrit = baseCrit;
		this.difficulty = difficulty;
		this.renderScale = renderScale;
		this.widthScale = widthScale;
		this.heightScale = heightScale;
		this.lengthScale = lengthScale;
	}

	@Override
	public String getSerializedName() {
		return id;
	}

	public MutableComponent displayName() {
		return Component.translatable("item.fishfight." + id);
	}

	/** 種類ごとの固有能力（あれば）。サメは「シャークマン覚醒」 */
	@Nullable
	public String uniqueAbility() {
		return this == SHARK ? "shark_man" : null;
	}

	public static FishSpecies byId(String id) {
		for (FishSpecies s : values()) {
			if (s.id.equals(id)) {
				return s;
			}
		}
		return CARP;
	}

	public enum Habitat {
		FRESH, OCEAN, ANY
	}
}
