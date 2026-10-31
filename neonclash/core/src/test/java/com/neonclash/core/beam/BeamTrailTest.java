package com.neonclash.core.beam;

import com.neonclash.core.math.Vec3d;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BeamTrailTest {
    static final BeamTrail.Settings S = new BeamTrail.Settings(4, 3);
    static final Vec3d A = new Vec3d(0, 0, 0), B = new Vec3d(10, 0, 0);

    @Test
    @DisplayName("区間は fadeTicks で消える")
    void expires() {
        BeamTrail t = new BeamTrail();
        t.add(A, B, 0xFF2E63, S);
        for (int i = 0; i < 3; i++) t.tick(S);
        assertEquals(1, t.segments().size());
        assertEquals(3, t.segments().iterator().next().age());
        t.tick(S);
        assertTrue(t.segments().isEmpty());
    }

    @Test
    @DisplayName("先に通った区間から消える（彗星の尾）")
    void olderGoesFirst() {
        BeamTrail t = new BeamTrail();
        t.add(A, B, 1, S);
        t.tick(S);
        t.tick(S);
        t.add(B, B.add(5, 0, 0), 2, S);
        t.tick(S);
        t.tick(S);
        assertEquals(1, t.segments().size());
        assertEquals(2, t.segments().iterator().next().color());
    }

    @Test
    @DisplayName("ビートの区間は印が付く")
    void boosted() {
        BeamTrail t = new BeamTrail();
        t.add(A, B, 1, S);
        t.add(A, B, 2, true, S);
        assertFalse(t.segments().iterator().next().boosted());
        assertTrue(t.segments().stream().anyMatch(BeamTrail.Segment::boosted));
    }

    @Test
    @DisplayName("上限を超えたら古いものから捨てる")
    void capacity() {
        BeamTrail t = new BeamTrail();
        for (int i = 1; i <= 5; i++) t.add(A, B, i, S);
        assertEquals(3, t.segments().size());
        assertEquals(3, t.segments().iterator().next().color());
    }

    @Test
    @DisplayName("濃さは 1 から 0 へ線形に減り、範囲外に出ない")
    void fade() {
        assertEquals(1f, BeamTrail.fade(0, 10), 1e-6);
        assertEquals(0.5f, BeamTrail.fade(5, 10), 1e-6);
        assertEquals(0f, BeamTrail.fade(10, 10), 1e-6);
        assertEquals(0f, BeamTrail.fade(99, 10), 1e-6);
    }

    @Test
    @DisplayName("カメラのすぐ近くは薄く細い。0 なら無効")
    void ramp() {
        assertEquals(0f, BeamTrail.ramp(0, 1.0), 1e-6);
        assertEquals(0.5f, BeamTrail.ramp(0.5, 1.0), 1e-6);
        assertEquals(1f, BeamTrail.ramp(3, 1.0), 1e-6);
        assertEquals(1f, BeamTrail.ramp(0, 0), 1e-6);
    }

    @Test
    @DisplayName("銃口へのずらしは遠くで 0 になる")
    void muzzleOffset() {
        assertEquals(1.0, BeamTrail.muzzleOffsetScale(0, 10), 1e-9);
        assertEquals(0.5, BeamTrail.muzzleOffsetScale(5, 10), 1e-9);
        assertEquals(0.0, BeamTrail.muzzleOffsetScale(40, 10), 1e-9);
        assertEquals(0.0, BeamTrail.muzzleOffsetScale(1, 0), 1e-9);
    }

    @Test
    @DisplayName("線分上の最近点：途中・両端の外・長さ 0")
    void closestOnSegment() {
        assertEquals(new Vec3d(4, 0, 0), BeamTrail.closestOnSegment(A, B, new Vec3d(4, 3, 0)));
        assertEquals(A, BeamTrail.closestOnSegment(A, B, new Vec3d(-5, 1, 0)));
        assertEquals(B, BeamTrail.closestOnSegment(A, B, new Vec3d(50, 0, 2)));
        assertEquals(A, BeamTrail.closestOnSegment(A, A, new Vec3d(1, 1, 1)));
    }

    @Test
    void rejectsBadSettings() {
        assertThrows(IllegalArgumentException.class, () -> new BeamTrail.Settings(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new BeamTrail.Settings(1, 0));
    }
}
