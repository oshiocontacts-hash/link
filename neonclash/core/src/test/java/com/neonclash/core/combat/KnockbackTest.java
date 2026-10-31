package com.neonclash.core.combat;

import com.neonclash.core.math.Vec3d;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KnockbackTest {
    private static void assertVec(Vec3d expected, Vec3d actual) {
        assertEquals(expected.x(), actual.x(), 1e-9);
        assertEquals(expected.y(), actual.y(), 1e-9);
        assertEquals(expected.z(), actual.z(), 1e-9);
    }

    @Test
    @DisplayName("向きの長さや上下の角度に関係なく、水平には strength だけ押す")
    void flattensDirection() {
        assertVec(new Vec3d(0.5, 0.1, 0), Knockback.impulse(new Vec3d(9, 0, 0), 0.5, 0.1, 0));
        assertVec(new Vec3d(0.5, 0.1, 0), Knockback.impulse(new Vec3d(3, -3, 0), 0.5, 0.1, 0));
    }

    @Test
    @DisplayName("真上からの力は持ち上げだけ")
    void straightDown() {
        assertVec(new Vec3d(0, 0.2, 0), Knockback.impulse(new Vec3d(0, -1, 0), 0.5, 0.2, 0));
    }

    @Test
    @DisplayName("耐性で弱まり、1 なら動かない。範囲外の値は丸める")
    void resistance() {
        assertVec(new Vec3d(0.25, 0.05, 0), Knockback.impulse(new Vec3d(1, 0, 0), 0.5, 0.1, 0.5));
        assertVec(Vec3d.ZERO, Knockback.impulse(new Vec3d(1, 0, 0), 0.5, 0.1, 1));
        assertVec(Vec3d.ZERO, Knockback.impulse(new Vec3d(1, 0, 0), 0.5, 0.1, 7));
        assertVec(new Vec3d(0.5, 0, 0), Knockback.impulse(new Vec3d(1, 0, 0), 0.5, 0, -3));
    }

    @Test
    @DisplayName("速度に足される")
    void adds() {
        assertVec(new Vec3d(0.3, 0.2, 0.1), Knockback.apply(new Vec3d(0.1, 0, 0.1), new Vec3d(0.2, 0.2, 0), 2));
    }

    @Test
    @DisplayName("水平の速さは上限で止まり、向きは保つ")
    void capsHorizontalSpeed() {
        Vec3d v = Knockback.apply(new Vec3d(3, 0, 4), Vec3d.ZERO, 1);
        assertEquals(0.6, v.x(), 1e-9);
        assertEquals(0.8, v.z(), 1e-9);
    }

    @Test
    @DisplayName("落下中でも上向きの力で持ち上がる。上向きの力が無ければ落下はそのまま")
    void falling() {
        assertEquals(0.2, Knockback.apply(new Vec3d(0, -0.8, 0), new Vec3d(0, 0.2, 0), 2).y(), 1e-9);
        assertEquals(-0.8, Knockback.apply(new Vec3d(0, -0.8, 0), new Vec3d(0.2, 0, 0), 2).y(), 1e-9);
    }

    @Test
    @DisplayName("連続で持ち上げても上向きの速さは上限まで")
    void capsLift() {
        assertEquals(1.0, Knockback.apply(new Vec3d(0, 0.9, 0), new Vec3d(0, 0.5, 0), 1).y(), 1e-9);
    }
}
