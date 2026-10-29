package com.fishfight.fish;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.BossEvent;

/**
 * 個体のレア度。ステータス倍率・出現率・ミニゲームの難しさが変わる。
 */
public enum FishRarity implements StringRepresentable {
	COMMON("common", "★", ChatFormatting.WHITE, BossEvent.BossBarColor.WHITE, 1.0f, 52f, 0.0f, 0.32f),
	UNCOMMON("uncommon", "★★", ChatFormatting.GREEN, BossEvent.BossBarColor.GREEN, 1.12f, 27f, 0.1f, 0.29f),
	RARE("rare", "★★★", ChatFormatting.AQUA, BossEvent.BossBarColor.BLUE, 1.27f, 13f, 0.25f, 0.26f),
	EPIC("epic", "★★★★", ChatFormatting.LIGHT_PURPLE, BossEvent.BossBarColor.PURPLE, 1.45f, 5.5f, 0.4f, 0.23f),
	LEGENDARY("legendary", "★★★★★", ChatFormatting.GOLD, BossEvent.BossBarColor.YELLOW, 1.7f, 2f, 0.6f, 0.2f),
	MYTHIC("mythic", "✦✦✦✦✦✦", ChatFormatting.RED, BossEvent.BossBarColor.RED, 2.0f, 0.5f, 0.85f, 0.17f);

	public static final Codec<FishRarity> CODEC = StringRepresentable.fromEnum(FishRarity::values);

	public final String id;
	public final String stars;
	public final ChatFormatting color;
	public final BossEvent.BossBarColor barColor;
	public final float statMultiplier;
	public final float weight;
	public final float difficultyBonus;
	/** ミニゲームのキャッチゾーンの大きさ（バー全体に対する割合） */
	public final float zoneSize;

	FishRarity(String id, String stars, ChatFormatting color, BossEvent.BossBarColor barColor, float statMultiplier,
			float weight, float difficultyBonus, float zoneSize) {
		this.id = id;
		this.stars = stars;
		this.color = color;
		this.barColor = barColor;
		this.statMultiplier = statMultiplier;
		this.weight = weight;
		this.difficultyBonus = difficultyBonus;
		this.zoneSize = zoneSize;
	}

	@Override
	public String getSerializedName() {
		return id;
	}

	public MutableComponent displayName() {
		return Component.translatable("fishfight.rarity." + id).withStyle(color);
	}

	public boolean isAtLeast(FishRarity other) {
		return ordinal() >= other.ordinal();
	}

	public static FishRarity byOrdinal(int ordinal) {
		FishRarity[] values = values();
		return values[Math.max(0, Math.min(values.length - 1, ordinal))];
	}
}
