package com.neonclash.core.skill;

import com.neonclash.core.math.Vec3d;
import com.neonclash.core.slow.SlowZone;
import com.neonclash.core.slow.SlowZones;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillsTest {
    static final ParrySkill.Settings P = new ParrySkill.Settings(6, 100, 0.5);

    @Test
    @DisplayName("パリィ：受付中の攻撃だけ弾く")
    void parryWindow() {
        ParrySkill p = new ParrySkill();
        assertFalse(p.tryParry(P));
        assertTrue(p.tryActivate(P, 1.0));
        for (int i = 0; i < 5; i++) p.tick();
        assertTrue(p.tryParry(P));
        p.tick();
        assertFalse(p.tryParry(P));
    }

    @Test
    @DisplayName("パリィ：クールダウン中は使えない。成功でクールダウン短縮")
    void parryCooldown() {
        ParrySkill p = new ParrySkill();
        p.tryActivate(P, 1.0);
        assertFalse(p.tryActivate(P, 1.0));
        assertTrue(p.tryParry(P));
        assertEquals(50, p.cooldown().remaining());
        for (int i = 0; i < 50; i++) p.tick();
        assertTrue(p.tryActivate(P, 1.0));
    }

    @Test
    @DisplayName("ラストスタンド：1 ライフに 1 回だけ")
    void lastStandOncePerLife() {
        LastStand ls = new LastStand();
        assertTrue(ls.tryTrigger());
        assertFalse(ls.tryTrigger());
        assertFalse(ls.tryTrigger());
        ls.rearm();
        assertTrue(ls.tryTrigger());
        assertEquals(2, ls.triggerCount());
    }

    @Test
    @DisplayName("範囲スロー：内側だけ減速、重なりは強い方、期限で消える")
    void slowZones() {
        SlowZones z = new SlowZones();
        z.add(new SlowZone(new Vec3d(0, 0, 0), 4, 0.3, 100));
        z.add(new SlowZone(new Vec3d(3, 0, 0), 4, 0.6, 50));
        assertEquals(0.3, z.factorAt(new Vec3d(1, 0, 0), 0), 1e-9);
        assertEquals(0.6, z.factorAt(new Vec3d(6, 0, 0), 0), 1e-9);
        assertEquals(1.0, z.factorAt(new Vec3d(20, 0, 0), 0), 1e-9);
        assertEquals(1.0, z.factorAt(new Vec3d(6, 0, 0), 50), 1e-9);
        z.prune(100);
        assertTrue(z.active().isEmpty());
    }

    @Test
    void cooldownBasics() {
        Cooldown c = new Cooldown();
        assertTrue(c.ready());
        c.start(2);
        assertFalse(c.ready());
        c.tick();
        assertEquals(0.5f, c.progress(), 1e-6);
        c.tick();
        assertTrue(c.ready());
    }
}
