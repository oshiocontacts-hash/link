package com.neonclash.core.fx;

import com.neonclash.core.math.Vec3d;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class FxTest {
    @Test
    @DisplayName("球面の向きは長さ 1 で、シードが同じなら同じ")
    void sphere() {
        Random a = new Random(5), b = new Random(5);
        for (int i = 0; i < 200; i++) {
            Vec3d v = Fx.sphere(a);
            assertEquals(1.0, v.length(), 1e-9);
            assertEquals(v, Fx.sphere(b));
        }
    }

    @Test
    @DisplayName("円錐の向きは半角の中に収まる（斜めの軸・真上の軸）")
    void cone() {
        Random r = new Random(7);
        for (Vec3d axis : new Vec3d[]{new Vec3d(2, 1, -3), new Vec3d(0, 5, 0)}) {
            for (int i = 0; i < 500; i++) {
                Vec3d v = Fx.cone(r, axis, 30);
                assertEquals(1.0, v.length(), 1e-9);
                assertTrue(v.dot(axis.normalize()) >= Math.cos(Math.toRadians(30)) - 1e-9);
            }
        }
    }

    @Test
    @DisplayName("軸が長さ 0 なら球面になる")
    void coneWithoutAxis() {
        assertEquals(1.0, Fx.cone(new Random(1), Vec3d.ZERO, 10).length(), 1e-9);
    }

    @Test
    @DisplayName("直交ベクトルは軸と直交する")
    void perpendicular() {
        for (Vec3d n : new Vec3d[]{new Vec3d(0, 1, 0), new Vec3d(1, 0, 0), new Vec3d(1, 2, 3).normalize()}) {
            Vec3d p = Fx.perpendicular(n);
            assertEquals(0.0, p.dot(n), 1e-9);
            assertEquals(1.0, p.length(), 1e-9);
        }
    }

    @Test
    @DisplayName("easeOut は 0..1 で範囲外を丸める")
    void easeOut() {
        assertEquals(0f, Fx.easeOut(-1f), 1e-6);
        assertEquals(1f, Fx.easeOut(2f), 1e-6);
        assertEquals(0.875f, Fx.easeOut(0.5f), 1e-6);
    }

    @Test
    @DisplayName("白へ寄せる")
    void whiten() {
        assertEquals(0xFF2E63, Fx.whiten(0xFF2E63, 0));
        assertEquals(0xFFFFFF, Fx.whiten(0xFF2E63, 1));
        assertEquals(0x808080, Fx.whiten(0x000000, 0.5f));
    }

    @Test
    @DisplayName("ダメージの文字列：整数はそのまま、端数は 1 桁")
    void damageText() {
        assertEquals("7", Fx.damageText(7f));
        assertEquals("8.4", Fx.damageText(8.4f));
        assertEquals("10", Fx.damageText(9.99f));
    }

    @Test
    @DisplayName("画面上の向き：正面 0、右 +90 度、左 -90 度、背後 ±180 度")
    void screenAngle() {
        // yaw 0 は +Z を向く。世界の角度は atan2(dz, dx) なので +Z は 90 度
        assertEquals(0, Fx.screenAngle(90, 0), 1e-9);
        // +Z を向いたときの右は -X（180 度）
        assertEquals(Math.PI / 2, Fx.screenAngle(180, 0), 1e-9);
        assertEquals(-Math.PI / 2, Fx.screenAngle(0, 0), 1e-9);
        assertEquals(Math.PI, Math.abs(Fx.screenAngle(-90, 0)), 1e-9);
        // yaw -90 は +X を向く
        assertEquals(0, Fx.screenAngle(0, -90), 1e-9);
    }
}
