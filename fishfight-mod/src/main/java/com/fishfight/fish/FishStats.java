package com.fishfight.fish;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 1匹ごとの個体データ。アイテムのデータコンポーネントとして保存される。
 */
public record FishStats(
		FishSpecies species,
		FishRarity rarity,
		float size,
		float weight,
		int hp,
		int atk,
		int def,
		int spd,
		int crit,
		Personality personality,
		List<Mutation> mutations,
		List<Skill> skills,
		String catcher,
		int wins) {

	public static final Codec<FishStats> CODEC = RecordCodecBuilder.create(i -> i.group(
			FishSpecies.CODEC.fieldOf("species").forGetter(FishStats::species),
			FishRarity.CODEC.fieldOf("rarity").forGetter(FishStats::rarity),
			Codec.FLOAT.fieldOf("size").forGetter(FishStats::size),
			Codec.FLOAT.fieldOf("weight").forGetter(FishStats::weight),
			Codec.INT.fieldOf("hp").forGetter(FishStats::hp),
			Codec.INT.fieldOf("atk").forGetter(FishStats::atk),
			Codec.INT.fieldOf("def").forGetter(FishStats::def),
			Codec.INT.fieldOf("spd").forGetter(FishStats::spd),
			Codec.INT.fieldOf("crit").forGetter(FishStats::crit),
			Personality.CODEC.fieldOf("personality").forGetter(FishStats::personality),
			Mutation.CODEC.listOf().optionalFieldOf("mutations", List.of()).forGetter(FishStats::mutations),
			Skill.CODEC.listOf().optionalFieldOf("skills", List.of()).forGetter(FishStats::skills),
			Codec.STRING.optionalFieldOf("catcher", "").forGetter(FishStats::catcher),
			Codec.INT.optionalFieldOf("wins", 0).forGetter(FishStats::wins)
	).apply(i, FishStats::new));

	public static final StreamCodec<ByteBuf, FishStats> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

	public FishStats {
		mutations = List.copyOf(mutations);
		skills = List.copyOf(skills);
	}

	public boolean has(Mutation mutation) {
		return mutations.contains(mutation);
	}

	public boolean has(Skill skill) {
		return skills.contains(skill);
	}

	public FishStats withWins(int newWins) {
		return new FishStats(species, rarity, size, weight, hp, atk, def, spd, crit, personality, mutations, skills, catcher, newWins);
	}

	/** ざっくりした総合力 */
	public int power() {
		int p = hp + atk * 4 + def * 3 + spd * 3 + crit * 2;
		if (has(Mutation.ARMS)) {
			p += 40;
		}
		if (has(Mutation.ELECTRIC) || has(Mutation.SPIKY)) {
			p += 25;
		}
		p += skills.size() * 30;
		return p;
	}

	/** バトル中に表示する魚の大きさ */
	public float renderScale() {
		float sizeMul = has(Mutation.GIANT) ? 2.2f : has(Mutation.TINY) ? 0.4f : 1.0f;
		float q = ((size / sizeMul) - species.minCm) / (float) (species.maxCm - species.minCm);
		q = Math.max(0f, Math.min(1f, q));
		float scale = species.renderScale * (0.85f + 0.3f * q);
		if (has(Mutation.GIANT)) {
			scale *= 1.6f;
		} else if (has(Mutation.TINY)) {
			scale *= 0.55f;
		}
		return scale;
	}

	/** 「[腕付き] マグロ」のような表示名（レア度の色付き） */
	public MutableComponent displayName() {
		MutableComponent name = Component.empty();
		for (Mutation m : mutations) {
			name.append(Component.literal("[").withStyle(m.color))
					.append(m.displayName())
					.append(Component.literal("] ").withStyle(m.color));
		}
		name.append(species.displayName().withStyle(rarity.color));
		return name;
	}

	public void appendTooltip(List<Component> lines) {
		lines.add(Component.literal(rarity.stars + " ").withStyle(rarity.color).append(rarity.displayName()));
		lines.add(Component.translatable("fishfight.tooltip.size",
				String.format(Locale.ROOT, "%.1f", size),
				String.format(Locale.ROOT, "%.2f", weight)).withStyle(ChatFormatting.GRAY));
		lines.add(Component.translatable("fishfight.tooltip.personality", personality.displayName()).withStyle(ChatFormatting.GRAY));
		lines.add(Component.translatable("fishfight.tooltip.stats1", hp, atk, def).withStyle(ChatFormatting.WHITE));
		lines.add(Component.translatable("fishfight.tooltip.stats2", spd, crit).withStyle(ChatFormatting.WHITE));
		lines.add(Component.translatable("fishfight.tooltip.power", power()).withStyle(ChatFormatting.GOLD));
		if (species.uniqueAbility() != null) {
			String unique = species.uniqueAbility();
			lines.add(Component.translatable("fishfight.tooltip.unique",
					Component.translatable("fishfight.unique." + unique).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
					.withStyle(ChatFormatting.GRAY));
			lines.add(Component.literal(" ").append(Component.translatable("fishfight.unique." + unique + ".desc"))
					.withStyle(ChatFormatting.DARK_GRAY));
		}
		if (!mutations.isEmpty()) {
			lines.add(Component.translatable("fishfight.tooltip.mutations", join(mutations.stream().map(Mutation::displayName).toList()))
					.withStyle(ChatFormatting.GRAY));
		}
		if (skills.isEmpty()) {
			lines.add(Component.translatable("fishfight.tooltip.no_skills").withStyle(ChatFormatting.DARK_GRAY));
		} else {
			lines.add(Component.translatable("fishfight.tooltip.skills").withStyle(ChatFormatting.GRAY));
			for (Skill skill : skills) {
				lines.add(Component.literal(" ・").withStyle(ChatFormatting.GRAY).append(skill.displayName())
						.append(Component.literal(" ").append(skill.description()).withStyle(ChatFormatting.DARK_GRAY)));
			}
		}
		if (wins > 0) {
			lines.add(Component.translatable("fishfight.tooltip.wins", wins).withStyle(ChatFormatting.YELLOW));
		}
		if (!catcher.isEmpty()) {
			lines.add(Component.translatable("fishfight.tooltip.catcher", catcher).withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	private static MutableComponent join(List<MutableComponent> parts) {
		MutableComponent joined = Component.empty();
		for (int n = 0; n < parts.size(); n++) {
			if (n > 0) {
				joined.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
			}
			joined.append(parts.get(n));
		}
		return joined;
	}
}
