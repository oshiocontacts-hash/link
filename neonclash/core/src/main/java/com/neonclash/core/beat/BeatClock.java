package com.neonclash.core.beat;

/**
 * サーバー共通のビート。時刻はミリ秒（double）で扱い、BPM が割り切れない値でも誤差を溜めない。
 * <p>
 * BPM を変えると、変更時点の直前の拍を新しい基準点にする。拍番号は連続したまま、以降の拍間隔だけが変わる。
 */
public final class BeatClock {

    /**
     * @param onBeat    猶予内か
     * @param beatIndex 最寄りの拍の番号
     * @param offsetMs  最寄りの拍からのずれ（負＝早い、正＝遅い）
     */
    public record Judgement(boolean onBeat, long beatIndex, double offsetMs) {
        public static final Judgement DISABLED = new Judgement(false, -1, Double.NaN);
    }

    private boolean enabled;
    private double bpm;
    private double anchorMs;
    private long anchorIndex;
    private double toleranceMs;

    public BeatClock(double bpm, double startMs, double toleranceMs, boolean enabled) {
        requirePositive(bpm);
        this.bpm = bpm;
        this.anchorMs = startMs;
        this.toleranceMs = Math.max(0, toleranceMs);
        this.enabled = enabled;
    }

    private static void requirePositive(double bpm) {
        if (!(bpm > 0)) {
            throw new IllegalArgumentException("bpm must be > 0: " + bpm);
        }
    }

    public double intervalMs() {
        return 60_000.0 / bpm;
    }

    public Judgement judge(double timeMs) {
        if (!enabled) {
            return Judgement.DISABLED;
        }
        double interval = intervalMs();
        double rel = (timeMs - anchorMs) / interval;
        long n = Math.round(rel);
        double offset = (timeMs - anchorMs) - n * interval;
        // 浮動小数の誤差で境界ちょうどが外れないよう、ごく小さい余裕を持たせる
        boolean on = Math.abs(offset) <= toleranceMs + 1.0e-6;
        return new Judgement(on, anchorIndex + n, offset);
    }

    /** 現在の拍の中での位相 0..1（0 が拍の頭）。HUD のクロスヘア脈動用。 */
    public double phase(double timeMs) {
        double rel = (timeMs - anchorMs) / intervalMs();
        return rel - Math.floor(rel);
    }

    /** timeMs 以前で最後に鳴った拍の番号。 */
    public long beatIndexAt(double timeMs) {
        return anchorIndex + (long) Math.floor((timeMs - anchorMs) / intervalMs());
    }

    public double beatTimeMs(long beatIndex) {
        return anchorMs + (beatIndex - anchorIndex) * intervalMs();
    }

    public void setBpm(double newBpm, double nowMs) {
        requirePositive(newBpm);
        long k = (long) Math.floor((nowMs - anchorMs) / intervalMs());
        anchorMs = anchorMs + k * intervalMs();
        anchorIndex += k;
        bpm = newBpm;
    }

    public void setToleranceMs(double toleranceMs) {
        this.toleranceMs = Math.max(0, toleranceMs);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public double bpm() {
        return bpm;
    }

    public double anchorMs() {
        return anchorMs;
    }

    public long anchorIndex() {
        return anchorIndex;
    }

    public double toleranceMs() {
        return toleranceMs;
    }

    /** クライアントへ同期した値から復元する。 */
    public void sync(double bpm, double anchorMs, long anchorIndex, double toleranceMs, boolean enabled) {
        requirePositive(bpm);
        this.bpm = bpm;
        this.anchorMs = anchorMs;
        this.anchorIndex = anchorIndex;
        this.toleranceMs = Math.max(0, toleranceMs);
        this.enabled = enabled;
    }
}
