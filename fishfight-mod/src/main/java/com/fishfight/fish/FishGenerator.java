package com.fishfight.fish;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

/**
 * 釣れる魚の抽選と、個体ステータスの生成。
 */
public final class FishGenerator {
	private FishGenerator() {
	}

	/** 釣った場所と幸運から魚を1匹決める */
	public static FishStats roll(RandomSource random, Holder<Biome> biome, int luck, String catcher) {
		FishSpecies.Habitat habitat = biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN)
				? FishSpecies.Habitat.OCEAN
				: FishSpecies.Habitat.FRESH;
		FishSpecies species = pickSpecies(random, habitat, luck);
		return generate(random, species, pickRarity(random, luck), catcher);
	}

	public static FishSpecies pickSpecies(RandomSource random, FishSpecies.Habitat habitat, int luck) {
		List<FishSpecies> pool = new ArrayList<>();
		float total = 0f;
		for (FishSpecies s : FishSpecies.values()) {
			if (s.habitat == habitat || s.habitat == FishSpecies.Habitat.ANY) {
				pool.add(s);
				total += speciesWeight(s, luck);
			}
		}
		float r = random.nextFloat() * total;
		for (FishSpecies s : pool) {
			r -= speciesWeight(s, luck);
			if (r <= 0f) {
				return s;
			}
		}
		return pool.get(pool.size() - 1);
	}

	private static float speciesWeight(FishSpecies s, int luck) {
		// 珍しい魚ほど幸運の恩恵が大きい
		return s.catchWeight < 10f ? s.catchWeight * (1f + 0.4f * luck) : s.catchWeight;
	}

	public static FishRarity pickRarity(RandomSource random, int luck) {
		float total = 0f;
		for (FishRarity r : FishRarity.values()) {
			total += rarityWeight(r, luck);
		}
		float roll = random.nextFloat() * total;
		for (FishRarity r : FishRarity.values()) {
			roll -= rarityWeight(r, luck);
			if (roll <= 0f) {
				return r;
			}
		}
		return FishRarity.COMMON;
	}

	private static float rarityWeight(FishRarity r, int luck) {
		if (r.isAtLeast(FishRarity.RARE)) {
			return r.weight * (float) Math.pow(1.35, Math.max(0, luck));
		}
		return r.weight;
	}

	public static FishStats generate(RandomSource random, FishSpecies species, @Nullable FishRarity rarity, String catcher) {
		return generate(random, species, rarity, catcher, null);
	}

	public static FishStats generate(RandomSource random, FishSpecies species, @Nullable FishRarity rarity, String catcher,
			@Nullable Mutation forced) {
		if (rarity == null) {
			rarity = pickRarity(random, 0);
		}
		List<Mutation> mutations = rollMutations(random, rarity, forced);
		Personality personality = Personality.values()[random.nextInt(Personality.values().length)];

		// 大きさ：レアなほど大きくなりやすい
		float q = (float) (0.5 + random.nextGaussian() * 0.2 + rarity.ordinal() * 0.06);
		q = Math.max(0.02f, Math.min(1.0f, q));
		float size = species.minCm + (species.maxCm - species.minCm) * q;
		if (mutations.contains(Mutation.GIANT)) {
			size *= 2.2f;
		} else if (mutations.contains(Mutation.TINY)) {
			size *= 0.4f;
		}
		float meters = size / 100f;
		float weight = species.density * meters * meters * meters * (0.85f + random.nextFloat() * 0.3f);
		float sizeFactor = 0.85f + q * 0.3f;

		float hpMul = personality.hpMul;
		float atkMul = personality.atkMul;
		float defMul = personality.defMul;
		float spdMul = personality.spdMul;
		for (Mutation m : mutations) {
			hpMul *= m.hpMul;
			atkMul *= m.atkMul;
			defMul *= m.defMul;
			spdMul *= m.spdMul;
		}
		float rm = rarity.statMultiplier;
		int hp = stat(random, species.baseHp * rm * sizeFactor * hpMul);
		int atk = stat(random, species.baseAtk * rm * atkMul);
		int def = stat(random, species.baseDef * rm * defMul);
		int spd = stat(random, species.baseSpd * rm * spdMul);
		int crit = species.baseCrit + personality.critBonus + random.nextInt(5) + rarity.ordinal();

		return new FishStats(species, rarity, size, weight, hp, atk, def, spd, crit, personality, mutations, catcher, 0);
	}

	private static int stat(RandomSource random, float value) {
		return Math.max(1, Math.round(value * (0.9f + random.nextFloat() * 0.2f)));
	}

	private static List<Mutation> rollMutations(RandomSource random, FishRarity rarity, @Nullable Mutation forced) {
		List<Mutation> result = new ArrayList<>();
		if (forced != null) {
			result.add(forced);
		}
		float bonus = 1f + rarity.ordinal() * 0.25f;
		for (Mutation m : Mutation.values()) {
			if (result.size() >= 3) {
				break;
			}
			if (result.contains(m)
					|| (m == Mutation.TINY && result.contains(Mutation.GIANT))
					|| (m == Mutation.GIANT && result.contains(Mutation.TINY))) {
				continue;
			}
			if (random.nextFloat() < m.chance * bonus) {
				result.add(m);
			}
		}
		return result;
	}
}
