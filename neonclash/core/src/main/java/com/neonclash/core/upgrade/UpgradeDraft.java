package com.neonclash.core.upgrade;

import com.neonclash.core.style.StyleRank;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * ラウンド間の強化 3 択の抽選。
 * <ol>
 *   <li>枠（武器・剣・移動・切り札）をランダムに選ぶ。{@code distinctCategories} なら同じ枠は1回まで（候補が尽きたら解禁）</li>
 *   <li>スタイルランクの重みでレア度を選ぶ。その枠に無いレア度は除外して重みを正規化する</li>
 *   <li>枠×レア度の中から1つ選ぶ</li>
 * </ol>
 * 同じ抽選内での重複は無し。所持済みの非スタック強化も候補から外す。
 */
public final class UpgradeDraft {
    private UpgradeDraft() {
    }

    public static List<UpgradeDef> draw(List<UpgradeDef> pool, StyleRank rank, RarityWeights weights,
                                        Set<String> owned, int count, boolean distinctCategories, Random rng) {
        Set<String> offered = new LinkedHashSet<>();
        Set<UpgradeCategory> usedCategories = new LinkedHashSet<>();
        List<UpgradeDef> result = new ArrayList<>();
        while (result.size() < count) {
            Map<UpgradeCategory, List<UpgradeDef>> byCategory = eligible(pool, owned, offered);
            if (byCategory.isEmpty()) {
                break;
            }
            List<UpgradeCategory> categories = new ArrayList<>(byCategory.keySet());
            if (distinctCategories) {
                List<UpgradeCategory> fresh = new ArrayList<>(categories);
                fresh.removeAll(usedCategories);
                if (!fresh.isEmpty()) {
                    categories = fresh;
                }
            }
            UpgradeCategory category = categories.get(rng.nextInt(categories.size()));
            List<UpgradeDef> candidates = byCategory.get(category);
            Rarity rarity = rollRarity(candidates, rank, weights, rng);
            List<UpgradeDef> ofRarity = candidates.stream().filter(d -> d.rarity() == rarity).toList();
            UpgradeDef pick = ofRarity.get(rng.nextInt(ofRarity.size()));
            result.add(pick);
            offered.add(pick.id());
            usedCategories.add(category);
        }
        return result;
    }

    private static Map<UpgradeCategory, List<UpgradeDef>> eligible(List<UpgradeDef> pool, Set<String> owned, Set<String> offered) {
        Map<UpgradeCategory, List<UpgradeDef>> map = new EnumMap<>(UpgradeCategory.class);
        Set<String> seen = new LinkedHashSet<>();
        for (UpgradeDef d : pool) {
            if (!seen.add(d.id()) || offered.contains(d.id()) || (!d.stackable() && owned.contains(d.id()))) {
                continue;
            }
            map.computeIfAbsent(d.category(), k -> new ArrayList<>()).add(d);
        }
        return map;
    }

    private static Rarity rollRarity(List<UpgradeDef> candidates, StyleRank rank, RarityWeights weights, Random rng) {
        Set<Rarity> available = new LinkedHashSet<>();
        for (UpgradeDef d : candidates) {
            available.add(d.rarity());
        }
        double total = 0;
        for (Rarity r : available) {
            total += weights.weight(rank, r);
        }
        if (total <= 0) {
            // このランクで重みのあるレア度がこの枠に無い：一番低いレア度を出す
            return available.stream().min(Enum::compareTo).orElseThrow();
        }
        double roll = rng.nextDouble() * total;
        Rarity last = null;
        for (Rarity r : Rarity.values()) {
            if (!available.contains(r)) {
                continue;
            }
            double w = weights.weight(rank, r);
            if (w <= 0) {
                continue;
            }
            last = r;
            if (roll < w) {
                return r;
            }
            roll -= w;
        }
        return last;
    }
}
