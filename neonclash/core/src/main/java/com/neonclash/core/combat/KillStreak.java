package com.neonclash.core.combat;

/** 連続キル。キル音の音程を上げるのに使う。 */
public final class KillStreak {
    public record Settings(int windowTicks, float basePitch, float pitchStep, float maxPitch) {
    }

    private int streak;
    private long lastKillTick = Long.MIN_VALUE;

    /** キルを記録し、そのキル音のピッチを返す。 */
    public float onKill(long nowTick, Settings s) {
        boolean chained = lastKillTick != Long.MIN_VALUE && nowTick - lastKillTick <= s.windowTicks();
        streak = chained ? streak + 1 : 1;
        lastKillTick = nowTick;
        return pitchFor(streak, s);
    }

    public static float pitchFor(int streak, Settings s) {
        float p = s.basePitch() + s.pitchStep() * Math.max(0, streak - 1);
        return Math.min(s.maxPitch(), p);
    }

    public void reset() {
        streak = 0;
        lastKillTick = Long.MIN_VALUE;
    }

    public int streak() {
        return streak;
    }
}
