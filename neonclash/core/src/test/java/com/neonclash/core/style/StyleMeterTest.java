package com.neonclash.core.style;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.neonclash.core.style.StyleEvent.*;
import static org.junit.jupiter.api.Assertions.*;

class StyleMeterTest {
    // C=100, B=250, A=450, S=700
    static final StyleMeter.Settings S = new StyleMeter.Settings(new int[]{100, 250, 450, 700}, 1000, Map.of(
            KILL, 60, AIR_KILL, 60, BLINK_KILL, 70, SWORD_KILL, 90, ON_BEAT, 10, PARRY, 40,
            DAMAGE_TAKEN, -50, DEATH, -200));

    @Test
    @DisplayName("初期は D")
    void startsAtD() {
        assertEquals(StyleRank.D, new StyleMeter(S).rank());
    }

    @Test
    @DisplayName("通常キルだけより、空中・ブリンク直後・剣キルの方が大きく上がる")
    void specialKillsRaiseMore() {
        StyleMeter plain = new StyleMeter(S);
        plain.apply(KILL);
        StyleMeter air = new StyleMeter(S);
        air.apply(KILL, AIR_KILL);
        StyleMeter blink = new StyleMeter(S);
        blink.apply(KILL, BLINK_KILL);
        StyleMeter sword = new StyleMeter(S);
        sword.apply(KILL, SWORD_KILL);
        assertTrue(air.points() > plain.points());
        assertTrue(blink.points() > plain.points());
        assertTrue(sword.points() > plain.points());
    }

    @Test
    @DisplayName("D→C→B→A→S と上がる")
    void climbsAllRanks() {
        StyleMeter m = new StyleMeter(S);
        StyleMeter.Change c = m.apply(KILL, SWORD_KILL); // 150
        assertTrue(c.rankedUp());
        assertEquals(StyleRank.C, m.rank());
        m.apply(KILL, AIR_KILL); // 270
        assertEquals(StyleRank.B, m.rank());
        m.apply(KILL, BLINK_KILL, AIR_KILL); // 460
        assertEquals(StyleRank.A, m.rank());
        m.apply(KILL, SWORD_KILL, BLINK_KILL, AIR_KILL); // 740
        assertEquals(StyleRank.S, m.rank());
    }

    @Test
    @DisplayName("被弾で下がる。ランクを跨げば rankedDown")
    void damageLowers() {
        StyleMeter m = new StyleMeter(S);
        m.apply(KILL, SWORD_KILL); // 150 = C
        StyleMeter.Change c = m.apply(DAMAGE_TAKEN); // 100 = C（境界ちょうど）
        assertFalse(c.rankedDown());
        assertEquals(StyleRank.C, m.rank());
        c = m.apply(DAMAGE_TAKEN); // 50 = D
        assertTrue(c.rankedDown());
        assertEquals(StyleRank.D, m.rank());
    }

    @Test
    @DisplayName("0 未満にも上限超えにもならない")
    void clamped() {
        StyleMeter m = new StyleMeter(S);
        m.apply(DEATH, DEATH);
        assertEquals(0, m.points());
        for (int i = 0; i < 50; i++) m.apply(KILL, SWORD_KILL);
        assertEquals(1000, m.points());
        assertEquals(StyleRank.S, m.rank());
    }

    @Test
    @DisplayName("しきい値ちょうどで昇格、1 足りなければ据え置き")
    void thresholdBoundary() {
        assertEquals(StyleRank.D, StyleMeter.rankFor(99, S));
        assertEquals(StyleRank.C, StyleMeter.rankFor(100, S));
        assertEquals(StyleRank.A, StyleMeter.rankFor(699, S));
        assertEquals(StyleRank.S, StyleMeter.rankFor(700, S));
    }

    @Test
    void progressToNext() {
        StyleMeter m = new StyleMeter(S);
        m.apply(KILL); // 60 / 100
        assertEquals(0.6f, m.progressToNext(), 1e-6);
    }

    @Test
    void invalidThresholds() {
        assertThrows(IllegalArgumentException.class, () -> new StyleMeter.Settings(new int[]{1, 2}, 10, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new StyleMeter.Settings(new int[]{5, 4, 6, 7}, 10, Map.of()));
    }

    @Test
    void rankUpDownHelpers() {
        assertEquals(StyleRank.S, StyleRank.S.up());
        assertEquals(StyleRank.D, StyleRank.D.down());
        assertEquals(StyleRank.B, StyleRank.C.up());
    }
}
