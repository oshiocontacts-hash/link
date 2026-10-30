package com.neonclash.core.style;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * スタイルランク D→C→B→A→S。ポイント制で、イベントごとの増減値としきい値は設定から渡す。
 */
public final class StyleMeter {

    /**
     * @param thresholds C, B, A, S に上がるのに必要なポイント（昇順・4要素）
     * @param maxPoints  ポイント上限
     * @param deltas     イベントごとの増減（被弾・死亡は負の値）
     */
    public record Settings(int[] thresholds, int maxPoints, Map<StyleEvent, Integer> deltas) {
        public Settings {
            if (thresholds.length != StyleRank.values().length - 1) {
                throw new IllegalArgumentException("thresholds must have " + (StyleRank.values().length - 1) + " entries");
            }
            for (int i = 1; i < thresholds.length; i++) {
                if (thresholds[i] < thresholds[i - 1]) {
                    throw new IllegalArgumentException("thresholds must be ascending");
                }
            }
            thresholds = thresholds.clone();
            deltas = new EnumMap<>(deltas.isEmpty() ? Map.of(StyleEvent.KILL, 0) : deltas);
        }

        public int delta(StyleEvent e) {
            return deltas.getOrDefault(e, 0);
        }
    }

    public record Change(StyleRank before, StyleRank after, int points) {
        public boolean rankedUp() {
            return after.ordinal() > before.ordinal();
        }

        public boolean rankedDown() {
            return after.ordinal() < before.ordinal();
        }
    }

    private final Settings settings;
    private int points;

    public StyleMeter(Settings settings) {
        this.settings = Objects.requireNonNull(settings);
    }

    public Change apply(StyleEvent... events) {
        StyleRank before = rank();
        int sum = 0;
        for (StyleEvent e : events) {
            sum += settings.delta(e);
        }
        points = Math.max(0, Math.min(settings.maxPoints(), points + sum));
        return new Change(before, rank(), points);
    }

    public StyleRank rank() {
        return rankFor(points, settings);
    }

    public static StyleRank rankFor(int points, Settings s) {
        StyleRank r = StyleRank.D;
        for (int i = 0; i < s.thresholds().length; i++) {
            if (points >= s.thresholds()[i]) {
                r = StyleRank.values()[i + 1];
            }
        }
        return r;
    }

    public int points() {
        return points;
    }

    /** 次のランクまでの進捗 0..1（S は常に 1）。 */
    public float progressToNext() {
        StyleRank r = rank();
        if (r == StyleRank.S) {
            return 1f;
        }
        int lo = r == StyleRank.D ? 0 : settings.thresholds()[r.ordinal() - 1];
        int hi = settings.thresholds()[r.ordinal()];
        return hi <= lo ? 1f : (float) (points - lo) / (hi - lo);
    }

    public void reset() {
        points = 0;
    }
}
