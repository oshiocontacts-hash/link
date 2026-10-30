package com.neonclash.core.movement;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * ブリンクのチャージ管理。
 * <p>
 * チャージは1つずつ順番に回復する（消費したチャージごとにクールダウンが積まれ、先頭から消化される）。
 * 消費時に渡したクールダウン長がそのチャージの回復時間になるので、ビートボーナス等の短縮は消費時に反映する。
 */
public final class BlinkCharges {
    private int maxCharges;
    private int charges;
    private final Deque<Integer> pending = new ArrayDeque<>();
    private int remaining;

    public BlinkCharges(int maxCharges) {
        if (maxCharges < 1) {
            throw new IllegalArgumentException("maxCharges must be >= 1");
        }
        this.maxCharges = maxCharges;
        this.charges = maxCharges;
    }

    /** チャージを1つ消費する。残りが無ければ false。 */
    public boolean tryConsume(int cooldownTicks) {
        if (charges <= 0) {
            return false;
        }
        charges--;
        pending.addLast(Math.max(0, cooldownTicks));
        if (pending.size() == 1) {
            remaining = pending.peekFirst();
        }
        settleZeroCooldowns();
        return true;
    }

    public void tick() {
        tick(1);
    }

    public void tick(int ticks) {
        int left = ticks;
        while (left > 0 && !pending.isEmpty()) {
            int step = Math.min(left, remaining);
            remaining -= step;
            left -= step;
            if (remaining == 0) {
                finishHead();
            }
        }
    }

    private void settleZeroCooldowns() {
        while (!pending.isEmpty() && remaining == 0) {
            finishHead();
        }
    }

    private void finishHead() {
        pending.removeFirst();
        charges = Math.min(maxCharges, charges + 1);
        remaining = pending.isEmpty() ? 0 : pending.peekFirst();
        if (!pending.isEmpty() && remaining == 0) {
            settleZeroCooldowns();
        }
    }

    /** 強化などで最大チャージ数が変わったとき。増えた分は即座に使える。 */
    public void setMaxCharges(int newMax) {
        if (newMax < 1) {
            throw new IllegalArgumentException("maxCharges must be >= 1");
        }
        int diff = newMax - maxCharges;
        maxCharges = newMax;
        if (diff > 0) {
            charges += diff;
        }
        charges = Math.min(charges, maxCharges);
        while (charges + pending.size() > maxCharges && !pending.isEmpty()) {
            pending.removeLast();
            remaining = pending.isEmpty() ? 0 : (pending.size() == 1 ? Math.min(remaining, pending.peekFirst()) : remaining);
        }
    }

    /** ラウンド開始時など：全回復。 */
    public void refill() {
        pending.clear();
        remaining = 0;
        charges = maxCharges;
    }

    public int charges() {
        return charges;
    }

    public int maxCharges() {
        return maxCharges;
    }

    /** 次のチャージ回復までの tick。回復待ちが無ければ 0。 */
    public int ticksUntilNextCharge() {
        return pending.isEmpty() ? 0 : remaining;
    }

    /** HUD 用：回復中チャージの進捗 0..1（回復待ちが無ければ 1）。 */
    public float rechargeProgress() {
        if (pending.isEmpty()) {
            return 1f;
        }
        int total = pending.peekFirst();
        return total <= 0 ? 1f : 1f - (float) remaining / total;
    }
}
