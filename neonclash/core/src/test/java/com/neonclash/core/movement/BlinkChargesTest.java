package com.neonclash.core.movement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlinkChargesTest {

    @Test
    @DisplayName("2チャージを使い切ると3回目は失敗")
    void twoChargesThenEmpty() {
        BlinkCharges b = new BlinkCharges(2);
        assertTrue(b.tryConsume(40));
        assertTrue(b.tryConsume(40));
        assertFalse(b.tryConsume(40));
        assertEquals(0, b.charges());
    }

    @Test
    @DisplayName("チャージは1つずつ順番に回復する")
    void sequentialRecharge() {
        BlinkCharges b = new BlinkCharges(2);
        b.tryConsume(40);
        b.tryConsume(40);
        b.tick(39);
        assertEquals(0, b.charges());
        b.tick();
        assertEquals(1, b.charges());
        assertEquals(40, b.ticksUntilNextCharge());
        b.tick(40);
        assertEquals(2, b.charges());
        assertEquals(0, b.ticksUntilNextCharge());
    }

    @Test
    @DisplayName("消費時のクールダウン長がそのチャージに使われる（ビートボーナスで短縮）")
    void perChargeCooldown() {
        BlinkCharges b = new BlinkCharges(2);
        b.tryConsume(20); // ビートに乗った
        b.tryConsume(40);
        b.tick(20);
        assertEquals(1, b.charges());
        b.tick(39);
        assertEquals(1, b.charges());
        b.tick(1);
        assertEquals(2, b.charges());
    }

    @Test
    @DisplayName("回復中に使っても先頭の回復は進み続ける")
    void consumeWhileRecharging() {
        BlinkCharges b = new BlinkCharges(2);
        b.tryConsume(40);
        b.tick(30);
        b.tryConsume(40);
        assertEquals(0, b.charges());
        b.tick(10);
        assertEquals(1, b.charges());
        b.tick(40);
        assertEquals(2, b.charges());
    }

    @Test
    @DisplayName("満タンなら tick しても増えない")
    void neverExceedsMax() {
        BlinkCharges b = new BlinkCharges(2);
        b.tick(1000);
        assertEquals(2, b.charges());
    }

    @Test
    @DisplayName("一度に大量に tick しても正しく回復")
    void bulkTick() {
        BlinkCharges b = new BlinkCharges(3);
        b.tryConsume(10);
        b.tryConsume(10);
        b.tryConsume(10);
        b.tick(25);
        assertEquals(2, b.charges());
        assertEquals(5, b.ticksUntilNextCharge());
    }

    @Test
    @DisplayName("クールダウン 0 は即回復")
    void zeroCooldown() {
        BlinkCharges b = new BlinkCharges(2);
        assertTrue(b.tryConsume(0));
        assertEquals(2, b.charges());
    }

    @Test
    @DisplayName("強化で最大チャージが増えると即座に使える")
    void increaseMax() {
        BlinkCharges b = new BlinkCharges(2);
        b.tryConsume(40);
        b.setMaxCharges(3);
        assertEquals(2, b.charges());
        b.tick(40);
        assertEquals(3, b.charges());
    }

    @Test
    void refillResets() {
        BlinkCharges b = new BlinkCharges(2);
        b.tryConsume(40);
        b.tryConsume(40);
        b.refill();
        assertEquals(2, b.charges());
        assertEquals(0, b.ticksUntilNextCharge());
        assertEquals(1f, b.rechargeProgress());
    }

    @Test
    void progress() {
        BlinkCharges b = new BlinkCharges(2);
        b.tryConsume(40);
        b.tick(10);
        assertEquals(0.25f, b.rechargeProgress(), 1e-6);
    }

    @Test
    void invalidMax() {
        assertThrows(IllegalArgumentException.class, () -> new BlinkCharges(0));
    }
}
