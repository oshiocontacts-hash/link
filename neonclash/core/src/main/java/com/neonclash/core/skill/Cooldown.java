package com.neonclash.core.skill;

/** 単純な tick クールダウン。 */
public final class Cooldown {
    private int remaining;
    private int total;

    public boolean ready() {
        return remaining <= 0;
    }

    public void start(int ticks) {
        total = Math.max(0, ticks);
        remaining = total;
    }

    public void tick() {
        if (remaining > 0) {
            remaining--;
        }
    }

    public void clear() {
        remaining = 0;
    }

    public int remaining() {
        return remaining;
    }

    public float progress() {
        return total <= 0 ? 1f : 1f - (float) remaining / total;
    }
}
