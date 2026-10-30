package com.neonclash.core.combat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwordChargeTest {
    private static final int WINDUP = 6, RECOVERY = 10, LOCKOUT = 4;

    @Test
    @DisplayName("溜めが満ちた tick にだけ斬撃が出る")
    void swingAfterWindup() {
        SwordCharge s = new SwordCharge();
        assertTrue(s.startWindup(WINDUP, RECOVERY, LOCKOUT));
        for (int i = 0; i < WINDUP - 1; i++) {
            assertFalse(s.tick(), "tick " + i);
        }
        assertTrue(s.tick());
        assertEquals(SwordCharge.State.RECOVERY, s.state());
    }

    @Test
    @DisplayName("溜め中は二重に溜め始められない")
    void noDoubleWindup() {
        SwordCharge s = new SwordCharge();
        s.startWindup(WINDUP, RECOVERY, LOCKOUT);
        assertFalse(s.startWindup(WINDUP, RECOVERY, LOCKOUT));
    }

    @Test
    @DisplayName("溜め中にハンドガン被弾 → 溜めリセット、斬撃は出ない")
    void interruptResetsWindup() {
        SwordCharge s = new SwordCharge();
        s.startWindup(WINDUP, RECOVERY, 0);
        s.tick();
        s.tick();
        assertTrue(s.windupProgress() > 0);
        assertTrue(s.interrupt());
        assertEquals(SwordCharge.State.IDLE, s.state());
        assertEquals(0f, s.windupProgress());
        for (int i = 0; i < WINDUP * 2; i++) {
            assertFalse(s.tick());
        }
        assertEquals(1, s.interruptCount());
    }

    @Test
    @DisplayName("リセット後に溜め直すと最初から溜め直し")
    void rewindupStartsFromZero() {
        SwordCharge s = new SwordCharge();
        s.startWindup(WINDUP, RECOVERY, 0);
        for (int i = 0; i < WINDUP - 1; i++) s.tick();
        s.interrupt();
        assertTrue(s.startWindup(WINDUP, RECOVERY, 0));
        for (int i = 0; i < WINDUP - 1; i++) {
            assertFalse(s.tick());
        }
        assertTrue(s.tick());
    }

    @Test
    @DisplayName("ロックアウトありなら、潰された直後は溜め直せない")
    void lockoutAfterInterrupt() {
        SwordCharge s = new SwordCharge();
        s.startWindup(WINDUP, RECOVERY, LOCKOUT);
        s.interrupt();
        assertEquals(SwordCharge.State.LOCKOUT, s.state());
        assertFalse(s.startWindup(WINDUP, RECOVERY, LOCKOUT));
        for (int i = 0; i < LOCKOUT; i++) s.tick();
        assertTrue(s.startWindup(WINDUP, RECOVERY, LOCKOUT));
    }

    @Test
    @DisplayName("溜め中以外の被弾は何もしない（斬撃後の硬直は短縮されない）")
    void interruptOutsideWindupIgnored() {
        SwordCharge s = new SwordCharge();
        assertFalse(s.interrupt());
        s.startWindup(1, RECOVERY, LOCKOUT);
        assertTrue(s.tick());
        assertFalse(s.interrupt());
        assertEquals(SwordCharge.State.RECOVERY, s.state());
        assertEquals(0, s.interruptCount());
    }

    @Test
    @DisplayName("硬直が終わるまで次の溜めは始められない")
    void recoveryBlocksNextSwing() {
        SwordCharge s = new SwordCharge();
        s.startWindup(1, RECOVERY, LOCKOUT);
        s.tick();
        for (int i = 0; i < RECOVERY - 1; i++) {
            s.tick();
            assertFalse(s.startWindup(1, RECOVERY, LOCKOUT));
        }
        s.tick();
        assertTrue(s.startWindup(1, RECOVERY, LOCKOUT));
    }

    @Test
    void progressRises() {
        SwordCharge s = new SwordCharge();
        s.startWindup(4, 0, 0);
        assertEquals(0f, s.windupProgress());
        s.tick();
        assertEquals(0.25f, s.windupProgress(), 1e-6);
        s.tick();
        assertEquals(0.5f, s.windupProgress(), 1e-6);
    }

    @Test
    @DisplayName("硬直 0 なら斬撃直後に IDLE")
    void zeroRecovery() {
        SwordCharge s = new SwordCharge();
        s.startWindup(1, 0, 0);
        assertTrue(s.tick());
        assertEquals(SwordCharge.State.IDLE, s.state());
    }
}
