package com.neonclash.core.upgrade;

import com.neonclash.core.style.StyleRank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UpgradeDraftTest {
    static final RarityWeights W = new RarityWeights()
            .set(StyleRank.D, 70, 25, 5, 0)
            .set(StyleRank.C, 55, 30, 12, 3)
            .set(StyleRank.B, 40, 35, 20, 5)
            .set(StyleRank.A, 25, 35, 28, 12)
            .set(StyleRank.S, 10, 30, 35, 25);

    /** 各枠 × 各レア度を 2 つずつ持つプール */
    static List<UpgradeDef> pool() {
        List<UpgradeDef> p = new ArrayList<>();
        for (UpgradeCategory c : UpgradeCategory.values())
            for (Rarity r : Rarity.values())
                for (int i = 0; i < 2; i++)
                    p.add(new UpgradeDef(c + "_" + r + "_" + i, c, r, UpgradeStat.GUN_DAMAGE, 0.1, i == 1));
        return p;
    }

    @RepeatedTest(20)
    @DisplayName("3 択に重複が無く、枠も重ならない")
    void threeDistinct() {
        List<UpgradeDef> d = UpgradeDraft.draw(pool(), StyleRank.B, W, Set.of(), 3, true, new Random());
        assertEquals(3, d.size());
        assertEquals(3, d.stream().map(UpgradeDef::id).distinct().count());
        assertEquals(3, d.stream().map(UpgradeDef::category).distinct().count());
    }

    @Test
    @DisplayName("ランクが高いほど平均レア度が上がる")
    void higherRankHigherRarity() {
        Map<StyleRank, Double> avg = new EnumMap<>(StyleRank.class);
        for (StyleRank rank : StyleRank.values()) {
            Random rng = new Random(42);
            double sum = 0;
            int n = 0;
            for (int i = 0; i < 4000; i++) {
                for (UpgradeDef def : UpgradeDraft.draw(pool(), rank, W, Set.of(), 3, true, rng)) {
                    sum += def.rarity().ordinal();
                    n++;
                }
            }
            avg.put(rank, sum / n);
        }
        for (int i = 1; i < StyleRank.values().length; i++) {
            StyleRank lo = StyleRank.values()[i - 1], hi = StyleRank.values()[i];
            assertTrue(avg.get(hi) > avg.get(lo), lo + "=" + avg.get(lo) + " " + hi + "=" + avg.get(hi));
        }
        // 実測が重みの期待値に近い（±0.05）
        for (StyleRank rank : StyleRank.values()) {
            assertEquals(W.expectedRarity(rank), avg.get(rank), 0.05, rank.name());
        }
    }

    @Test
    @DisplayName("重み 0 のレア度は出ない（D ランクで LEGENDARY は出ない）")
    void zeroWeightNeverDrawn() {
        Random rng = new Random(1);
        for (int i = 0; i < 5000; i++) {
            for (UpgradeDef def : UpgradeDraft.draw(pool(), StyleRank.D, W, Set.of(), 3, true, rng)) {
                assertNotEquals(Rarity.LEGENDARY, def.rarity());
            }
        }
    }

    @Test
    @DisplayName("所持済みの非スタック強化は出ない。スタック可能なものは出る")
    void ownedExcluded() {
        List<UpgradeDef> p = pool();
        Set<String> owned = new HashSet<>();
        for (UpgradeDef d : p) owned.add(d.id()); // 全部所持
        Random rng = new Random(3);
        for (int i = 0; i < 500; i++) {
            for (UpgradeDef def : UpgradeDraft.draw(p, StyleRank.S, W, owned, 3, true, rng)) {
                assertTrue(def.stackable(), def.id());
            }
        }
    }

    @Test
    @DisplayName("候補が 3 つ未満なら出せる分だけ")
    void notEnoughCandidates() {
        List<UpgradeDef> p = List.of(
                UpgradeDef.parse("a|WEAPON|COMMON|GUN_DAMAGE|0.1|false"),
                UpgradeDef.parse("b|SWORD|COMMON|SWORD_RANGE|0.5|false"));
        List<UpgradeDef> d = UpgradeDraft.draw(p, StyleRank.A, W, Set.of("a"), 3, true, new Random(0));
        assertEquals(List.of(p.get(1)), d);
    }

    @Test
    @DisplayName("枠に重みのあるレア度が無ければその枠の最低レア度")
    void fallbackRarity() {
        List<UpgradeDef> p = List.of(UpgradeDef.parse("leg|ULTIMATE|LEGENDARY|LAST_STAND_DURATION|20|false"));
        List<UpgradeDef> d = UpgradeDraft.draw(p, StyleRank.D, W, Set.of(), 3, true, new Random(0));
        assertEquals(1, d.size());
    }

    @Test
    @DisplayName("プール内で ID が重複していても 3 択には 1 回しか出ない")
    void duplicateIdsInPool() {
        UpgradeDef a = UpgradeDef.parse("same|WEAPON|COMMON|GUN_DAMAGE|0.1|true");
        List<UpgradeDef> d = UpgradeDraft.draw(List.of(a, a, a), StyleRank.C, W, Set.of(), 3, true, new Random(0));
        assertEquals(1, d.size());
    }

    @Test
    @DisplayName("枠の種類が 3 未満でも distinctCategories で詰まらない")
    void fewCategories() {
        List<UpgradeDef> p = new ArrayList<>();
        for (int i = 0; i < 5; i++) p.add(new UpgradeDef("w" + i, UpgradeCategory.WEAPON, Rarity.COMMON, UpgradeStat.GUN_DAMAGE, 0.1, false));
        List<UpgradeDef> d = UpgradeDraft.draw(p, StyleRank.C, W, Set.of(), 3, true, new Random(0));
        assertEquals(3, d.size());
        assertEquals(3, d.stream().map(UpgradeDef::id).distinct().count());
    }

    @Test
    @DisplayName("同じシードなら同じ結果")
    void deterministic() {
        assertEquals(UpgradeDraft.draw(pool(), StyleRank.A, W, Set.of(), 3, true, new Random(7)),
                UpgradeDraft.draw(pool(), StyleRank.A, W, Set.of(), 3, true, new Random(7)));
    }

    @Test
    @DisplayName("config 文字列のパースと往復")
    void parseRoundTrip() {
        UpgradeDef d = UpgradeDef.parse(" blink_cd | movement | rare | BLINK_COOLDOWN | 0.2 | false ");
        assertEquals("blink_cd", d.id());
        assertEquals(UpgradeCategory.MOVEMENT, d.category());
        assertEquals(Rarity.RARE, d.rarity());
        assertEquals(d, UpgradeDef.parse(d.serialize()));
        assertThrows(IllegalArgumentException.class, () -> UpgradeDef.parse("a|b"));
        assertThrows(IllegalArgumentException.class, () -> UpgradeDef.parse("a|NOPE|COMMON|GUN_DAMAGE|1|true"));
    }

    @Test
    @DisplayName("UpgradeStats：加算と倍率変換")
    void stats() {
        UpgradeStats s = new UpgradeStats();
        s.add(UpgradeDef.parse("a|MOVEMENT|COMMON|BLINK_COOLDOWN|0.2|true"));
        s.add(UpgradeDef.parse("a|MOVEMENT|COMMON|BLINK_COOLDOWN|0.2|true"));
        s.add(UpgradeDef.parse("g|WEAPON|RARE|GUN_DAMAGE|0.15|false"));
        assertEquals(0.6, s.reductionMultiplier(UpgradeStat.BLINK_COOLDOWN, 0.2), 1e-9);
        assertEquals(1.15, s.increaseMultiplier(UpgradeStat.GUN_DAMAGE), 1e-9);
        for (int i = 0; i < 10; i++) s.add(UpgradeDef.parse("a|MOVEMENT|COMMON|BLINK_COOLDOWN|0.2|true"));
        assertEquals(0.2, s.reductionMultiplier(UpgradeStat.BLINK_COOLDOWN, 0.2), 1e-9);
        assertTrue(s.owns("g"));
    }
}
