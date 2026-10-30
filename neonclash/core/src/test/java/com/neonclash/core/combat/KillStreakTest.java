package com.neonclash.core.combat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KillStreakTest {
    private static final KillStreak.Settings S = new KillStreak.Settings(100, 1.0f, 0.12f, 1.6f);

    @Test
    @DisplayName("連続キルで音程が上がり、上限で止まる")
    void pitchRises() {
        KillStreak k = new KillStreak();
        assertEquals(1.0f, k.onKill(0, S), 1e-6);
        assertEquals(1.12f, k.onKill(50, S), 1e-6);
        assertEquals(1.24f, k.onKill(150, S), 1e-6);
        for (int i = 0; i < 10; i++) k.onKill(200 + i, S);
        assertEquals(1.6f, k.onKill(300, S), 1e-6);
    }

    @Test
    @DisplayName("間隔が空くとリセット")
    void windowExpires() {
        KillStreak k = new KillStreak();
        k.onKill(0, S);
        k.onKill(10, S);
        assertEquals(1.0f, k.onKill(111, S), 1e-6);
        assertEquals(1, k.streak());
    }
}
