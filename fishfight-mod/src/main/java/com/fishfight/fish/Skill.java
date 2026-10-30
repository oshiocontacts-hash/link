package com.fishfight.fish;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringRepresentable;

/**
 * 魚ごとに持っていたり持っていなかったりする自動発動スキル。
 * 発動条件と効果は {@link com.fishfight.entity.BattleFishEntity} に書いてある。
 */
public enum Skill implements StringRepresentable {
	/** HP半分以下で腕が生える：攻撃力アップ＆パンチ連打 */
	ARM_SPROUT("arm_sprout", 0.22f, ChatFormatting.YELLOW),
	/** 陸で3秒跳ね続けると脚が生える：跳ねずに歩いて戦える */
	LEG_SPROUT("leg_sprout", 0.18f, ChatFormatting.YELLOW),
	/** HP30%以下で巨大化 */
	GIANTIZE("giantize", 0.06f, ChatFormatting.DARK_RED),
	/** HP35%以下で一度だけ回復 */
	REGENERATE("regenerate", 0.08f, ChatFormatting.GREEN),
	/** 近くの相手に電撃＋しびれ */
	DISCHARGE("discharge", 0.06f, ChatFormatting.AQUA),
	/** 離れた相手に突進 */
	CHARGE("charge", 0.08f, ChatFormatting.RED),
	/** 離れた相手に水鉄砲 */
	WATER_GUN("water_gun", 0.07f, ChatFormatting.BLUE),
	/** 攻撃を受けるとふくらんで防御アップ */
	PUFF_UP("puff_up", 0.05f, ChatFormatting.GOLD),
	/** 倒れる攻撃を一度だけHP1で耐える */
	ENDURE("endure", 0.05f, ChatFormatting.DARK_AQUA),
	/** HP40%以下で逆上して攻撃が速くなる */
	FRENZY("frenzy", 0.07f, ChatFormatting.DARK_PURPLE);

	public static final Codec<Skill> CODEC = StringRepresentable.fromEnum(Skill::values);

	public final String id;
	public final float chance;
	public final ChatFormatting color;

	Skill(String id, float chance, ChatFormatting color) {
		this.id = id;
		this.chance = chance;
		this.color = color;
	}

	@Override
	public String getSerializedName() {
		return id;
	}

	public MutableComponent displayName() {
		return Component.translatable("fishfight.skill." + id).withStyle(color);
	}

	public MutableComponent description() {
		return Component.translatable("fishfight.skill." + id + ".desc");
	}

	public static Skill byId(String id) {
		for (Skill s : values()) {
			if (s.id.equals(id)) {
				return s;
			}
		}
		return null;
	}
}
