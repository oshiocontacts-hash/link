package com.neonclash.core.movement;

import com.neonclash.core.math.Box;
import com.neonclash.core.math.CollisionQuery;
import com.neonclash.core.math.Vec3d;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BlinkResolverTest {
    private static final double W = 0.6, H = 1.8;
    private static final BlinkResolver.Settings S = new BlinkResolver.Settings(6, 0.25, 0.6, 0.6, 0.5);

    /** 1x1x1 ブロックのグリッド世界。y<0 は床。 */
    static final class Grid implements CollisionQuery {
        final Set<String> solid = new HashSet<>();

        Grid block(int x, int y, int z) {
            solid.add(x + "," + y + "," + z);
            return this;
        }

        Grid wallX(int x, int fromY, int toY, int fromZ, int toZ) {
            for (int y = fromY; y <= toY; y++)
                for (int z = fromZ; z <= toZ; z++) block(x, y, z);
            return this;
        }

        @Override
        public boolean isFree(Box b) {
            for (int x = (int) Math.floor(b.minX()); x < Math.ceil(b.maxX()); x++)
                for (int y = (int) Math.floor(b.minY()); y < Math.ceil(b.maxY()); y++)
                    for (int z = (int) Math.floor(b.minZ()); z < Math.ceil(b.maxZ()); z++) {
                        if (y < 0 || solid.contains(x + "," + y + "," + z)) {
                            Box cell = new Box(x, y, z, x + 1, y + 1, z + 1);
                            if (cell.intersects(b)) return false;
                        }
                    }
            return true;
        }
    }

    private static final Vec3d START = new Vec3d(0.5, 0, 0.5);
    private static final Vec3d EAST = new Vec3d(1, 0, 0);

    @Test
    @DisplayName("障害物が無ければ全距離進む")
    void fullDistance() {
        var r = BlinkResolver.resolve(START, EAST, W, H, S, new Grid()).orElseThrow();
        assertEquals(6.5, r.destination().x(), 1e-6);
        assertEquals(0, r.destination().y(), 1e-6);
    }

    @Test
    @DisplayName("壁の手前で止まり、壁を貫通しない")
    void stopsBeforeWall() {
        Grid g = new Grid().wallX(3, 0, 3, -2, 2);
        var r = BlinkResolver.resolve(START, EAST, W, H, S, g).orElseThrow();
        assertTrue(r.destination().x() + W / 2 <= 3.0 + 1e-9, "x=" + r.destination().x());
        assertTrue(r.destination().x() > 2.0);
        assertTrue(g.isFree(Box.ofEntity(r.destination(), W, H)));
    }

    @Test
    @DisplayName("薄い（1ブロック）壁でも向こう側に抜けない")
    void thinWallNotTunnelled() {
        Grid g = new Grid().wallX(2, 0, 5, -3, 3);
        var big = new BlinkResolver.Settings(20, 0.9, 0.6, 0.6, 0.5); // 間隔を荒くしても内部で細かく刻む
        var r = BlinkResolver.resolve(START, EAST, W, H, big, g).orElseThrow();
        assertTrue(r.destination().x() < 2.0);
    }

    @Test
    @DisplayName("床を見下ろして撃っても床にめり込まず、床に沿って進む")
    void slidesAlongFloor() {
        Grid g = new Grid();
        Vec3d down = new Vec3d(1, -0.3, 0);
        var r = BlinkResolver.resolve(START, down, W, H, S, g).orElseThrow();
        // 床より下には行かず、浮いても補正 1 ステップ分未満（着地は重力に任せる）
        assertTrue(r.destination().y() >= 0 && r.destination().y() < S.step(), "y=" + r.destination().y());
        assertTrue(r.destination().x() > 4);
    }

    @Test
    @DisplayName("持ち上げ量以内の段差は越え、超える段差の手前で止まる")
    void stepUp() {
        Grid g = new Grid();
        for (int z = -2; z <= 2; z++) { g.block(2, 0, z); g.block(3, 0, z); }
        var oneBlock = new BlinkResolver.Settings(6, 0.25, 1.0, 0.6, 0.5);
        var r = BlinkResolver.resolve(START, EAST, W, H, oneBlock, g).orElseThrow();
        assertEquals(1.0, r.destination().y(), 1e-6);
        assertTrue(r.destination().x() > 3);
        var low = BlinkResolver.resolve(START, EAST, W, H, S, g).orElseThrow();
        assertTrue(low.destination().x() + W / 2 <= 2.0 + 1e-9);
    }

    @Test
    @DisplayName("天井に向けても天井にめり込まない")
    void ceilingNotPenetrated() {
        Grid g = new Grid();
        for (int x = -3; x <= 10; x++)
            for (int z = -3; z <= 3; z++) g.block(x, 3, z); // 天井 y=3
        Vec3d up = new Vec3d(1, 1, 0);
        var r = BlinkResolver.resolve(START, up, W, H, S, g).orElseThrow();
        assertTrue(r.destination().y() + H <= 3.0 + 1e-9, "head=" + (r.destination().y() + H));
        assertTrue(g.isFree(Box.ofEntity(r.destination(), W, H)));
    }

    @Test
    @DisplayName("真上が薄い天井でも、持ち上げ補正で天井の上に抜けない")
    void liftDoesNotTunnelCeiling() {
        Grid g = new Grid();
        for (int x = -3; x <= 10; x++)
            for (int z = -3; z <= 3; z++) g.block(x, 2, z); // 頭のすぐ上に厚さ1の天井
        var r = BlinkResolver.resolve(START, new Vec3d(0, 1, 0), W, H, S, g);
        assertTrue(r.isEmpty() || r.get().destination().y() + H <= 2.0 + 1e-9);
    }

    @Test
    @DisplayName("目の前が壁なら失敗（チャージは消費しない想定）")
    void blockedImmediately() {
        Grid g = new Grid().wallX(1, 0, 3, -2, 2);
        Optional<BlinkResolver.Result> r = BlinkResolver.resolve(START, EAST, W, H, S, g);
        assertTrue(r.isEmpty());
    }

    @Test
    void zeroLookRejected() {
        assertTrue(BlinkResolver.resolve(START, Vec3d.ZERO, W, H, S, new Grid()).isEmpty());
    }
}
