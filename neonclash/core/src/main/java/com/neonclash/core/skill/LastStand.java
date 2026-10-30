package com.neonclash.core.skill;

/** 切り札：ラストスタンド。1ライフにつき1回だけ、致命傷を無効化して発動する。 */
public final class LastStand {
    private boolean available = true;
    private int triggers;

    /** 致命傷を受けた。まだ使っていなければ消費して true。 */
    public boolean tryTrigger() {
        if (!available) {
            return false;
        }
        available = false;
        triggers++;
        return true;
    }

    /** 新しいライフ（ラウンド開始）で再装填。 */
    public void rearm() {
        available = true;
    }

    public boolean isAvailable() {
        return available;
    }

    public int triggerCount() {
        return triggers;
    }
}
