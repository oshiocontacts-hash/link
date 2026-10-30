package com.neonclash.core.upgrade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** プレイヤーが取った強化の一覧と、その合計値。 */
public final class UpgradeStats {
    private final List<UpgradeDef> owned = new ArrayList<>();
    private final Map<UpgradeStat, Double> totals = new EnumMap<>(UpgradeStat.class);

    public void add(UpgradeDef def) {
        owned.add(def);
        totals.merge(def.stat(), def.value(), Double::sum);
    }

    public double get(UpgradeStat stat) {
        return totals.getOrDefault(stat, 0.0);
    }

    /** 「-x（割合）」系を倍率に変換する。下限 {@code floor}。 */
    public double reductionMultiplier(UpgradeStat stat, double floor) {
        return Math.max(floor, 1.0 - get(stat));
    }

    /** 「+x（割合）」系を倍率に変換する。 */
    public double increaseMultiplier(UpgradeStat stat) {
        return 1.0 + get(stat);
    }

    public List<UpgradeDef> owned() {
        return Collections.unmodifiableList(owned);
    }

    public boolean owns(String id) {
        return owned.stream().anyMatch(d -> d.id().equals(id));
    }

    public void clear() {
        owned.clear();
        totals.clear();
    }
}
