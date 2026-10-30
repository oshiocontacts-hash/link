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
	// 体長はタツノオトシゴなのに、重さと見た目は馬
	SEAHORSE("seahorse", Habitat.OCEAN, 8f, 0x8B5A2B, 12, 30, 60000f, 95, 17, 13, 20, 7, 1.5f, 1.0f, 1.0f, 1.0f, 1.0f),
	// HPは多いのに、驚くとすぐ死ぬ
	SUNFISH("sunfish", Habitat.OCEAN, 5f, 0xA9B4BE, 100, 330, 40f, 200, 10, 10, 6, 3, 1.0f, 1.15f, 0.5f, 2.2f, 0.6f),
	// 当てるたびに出世して名前が変わる（ツバス→ハマチ→メジロ→ブリ）
	YELLOWTAIL("yellowtail", Habitat.OCEAN, 10f, 0x6E8FB0, 30, 100, 12f, 80, 15, 12, 15, 6, 1.2f, 0.85f, 0.9f, 0.9f, 1.4f),
	// 倒されると相手に針千本を飲ませる
	PORCUPINEFISH("porcupinefish", Habitat.OCEAN, 6f, 0xC9B98A, 20, 50, 30f, 75, 11, 18, 9, 5, 1.1f, 0.75f, 1.4f, 1.3f, 0.9f),
	// 攻撃すると相手が回復する。そのかわり相手は気持ちよすぎて昇天する
	DOCTOR_FISH("doctor_fish", Habitat.FRESH, 8f, 0x8C8C7A, 5, 12, 15f, 60, 1, 10, 22, 0, 0.8f, 0.5f, 0.7f, 0.8f, 1.0f),
	// 見た目はイワシの群れ
	SARDINE("sardine", Habitat.OCEAN, 20f, 0x9FB4C8, 10, 25, 9f, 70, 7, 8, 24, 10, 1.0f, 0.4f, 0.7f, 0.7f, 1.1f),
	// 地面から縦に生えていて動かない。近づくと引っ込む
	GARDEN_EEL("garden_eel", Habitat.OCEAN, 7f, 0xE8E2C8, 30, 60, 1.5f, 75, 18, 12, 16, 10, 1.3f, 0.8f, 0.35f, 0.35f, 2.6f),
	// 魚ではない
	TAIYAKI("taiyaki", Habitat.ANY, 3f, 0xC98A3D, 12, 18, 30f, 55, 6, 6, 6, 0, 0.6f, 0.7f, 0.6f, 1.0f, 1.0f),
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

	/** 種類ごとの固有能力（あれば）。サメは「シャークマン覚醒」、シーホースは「馬力」 */
	@Nullable
	public String uniqueAbility() {
		return switch (this) {
			case SHARK -> "shark_man";
			case SEAHORSE -> "horsepower";
			case SUNFISH -> "tofu_mental";
			case YELLOWTAIL -> "promotion";
			case PORCUPINEFISH -> "pinky_promise";
			case DOCTOR_FISH -> "skin_care";
			case SARDINE -> "school";
			case GARDEN_EEL -> "hide";
			case TAIYAKI -> "anko";
			default -> null;
		};
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
