package com.neonclash.core.fx;

import com.neonclash.core.math.Vec3d;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SparkTest {
    static final Spark.Look LOOK = new Spark.Look(4, 0.1f, 0xFF2E63, 0.5, 0.1, false);

    private static Spark spark() {
        return new Spark(Vec3d.ZERO, new Vec3d(1, 0, 0), LOOK, 0f);
    }

    @Test
    @DisplayName("速度で進み、減衰と重力が掛かる")
    void physics() {
        Spark s = spark();
        s.tick();
        assertEquals(1.0, s.position(1f).x(), 1e-9);
        assertEquals(0.5, s.velocity().x(), 1e-9);
        assertEquals(-0.1, s.velocity().y(), 1e-9);
        s.tick();
        assertEquals(1.5, s.position(1f).x(), 1e-9);
        assertEquals(-0.1, s.position(1f).y(), 1e-9);
    }

    @Test
    @DisplayName("tick の間を補間する")
    void interpolates() {
        Spark s = spark();
        s.tick();
        assertEquals(0.5, s.position(0.5f).x(), 1e-9);
    }

    @Test
    @DisplayName("明るさは 1 から 0 へ下がる")
    void brightness() {
        Spark s = spark();
        assertEquals(1f, s.brightness(0f), 1e-6);
        s.tick();
        s.tick();
        assertEquals(0.75f, s.brightness(0f), 1e-6);
        for (int i = 0; i < 8; i++) s.tick();
        assertEquals(0f, s.brightness(0f), 1e-6);
    }

    @Test
    @DisplayName("寿命で消える")
    void expires() {
        SparkField f = new SparkField();
        f.add(spark(), 8);
        for (int i = 0; i < 3; i++) f.tick();
        assertEquals(1, f.sparks().size());
        f.tick();
        assertTrue(f.sparks().isEmpty());
    }

    @Test
    @DisplayName("上限を超えたら古いものから捨てる")
    void capacity() {
        SparkField f = new SparkField();
        Spark first = spark(), second = spark(), third = spark();
        f.add(first, 2);
        f.add(second, 2);
        f.add(third, 2);
        assertEquals(2, f.sparks().size());
        assertFalse(f.sparks().contains(first));
    }

    @Test
    void rejectsBadLife() {
        assertThrows(IllegalArgumentException.class, () -> new Spark.Look(0, 1f, 0, 1, 0, false));
    }
}
