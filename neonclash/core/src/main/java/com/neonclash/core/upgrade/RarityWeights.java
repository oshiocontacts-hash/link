package com.neonclash.core.upgrade;

import com.neonclash.core.style.StyleRank;

import java.util.EnumMap;
import java.util.Map;

/** スタイルランクごとのレア度の重み（COMMON, RARE, EPIC, LEGENDARY の順）。 */
public final class RarityWeights {
    private final Map<StyleRank, double[]> table = new EnumMap<>(StyleRank.class);

    public RarityWeights set(StyleRank rank, double... weights) {
        if (weights.length != Rarity.values().length) {
            throw new IllegalArgumentException("need " + Rarity.values().length + " weights for " + rank);
        }
        for (double w : weights) {
            if (w < 0 || Double.isNaN(w)) {
                throw new IllegalArgumentException("weights must be >= 0");
            }
        }
        table.put(rank, weights.clone());
        return this;
    }

    public double weight(StyleRank rank, Rarity rarity) {
        double[] w = table.get(rank);
        if (w == null) {
            throw new IllegalStateException("no weights for rank " + rank);
        }
        return w[rarity.ordinal()];
    }

    /** ランクでの期待レア度（ordinal の加重平均）。 */
    public double expectedRarity(StyleRank rank) {
        double sum = 0, total = 0;
        for (Rarity r : Rarity.values()) {
            sum += weight(rank, r) * r.ordinal();
            total += weight(rank, r);
        }
        return total == 0 ? 0 : sum / total;
    }
}
