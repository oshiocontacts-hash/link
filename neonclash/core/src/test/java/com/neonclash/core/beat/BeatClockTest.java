package com.neonclash.core.beat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class BeatClockTest {
    // 120 BPM = 500ms 間隔、猶予 ±100ms
    private BeatClock clock() {
        return new BeatClock(120, 0, 100, true);
    }

    @Test
    @DisplayName("拍ちょうどは猶予内")
    void exactBeatIsOnBeat() {
        BeatClock c = clock();
        BeatClock.Judgement j = c.judge(1500);
        assertTrue(j.onBeat());
        assertEquals(3, j.beatIndex());
        assertEquals(0, j.offsetMs(), 1e-9);
    }

    @ParameterizedTest(name = "拍から {0}ms ずれ → 猶予内")
    @ValueSource(doubles = {-100, -99.9, -50, 50, 99.9, 100})
    void insideTolerance(double offset) {
        assertTrue(clock().judge(2000 + offset).onBeat());
    }

    @ParameterizedTest(name = "拍から {0}ms ずれ → 猶予外")
    @ValueSource(doubles = {-100.1, -101, -249, 101, 100.1, 249})
    void outsideTolerance(double offset) {
        assertFalse(clock().judge(2000 + offset).onBeat());
    }

    @Test
    @DisplayName("境界：拍と拍のちょうど中間は最寄りの拍を選ぶが猶予外")
    void midpointBetweenBeats() {
        BeatClock.Judgement j = clock().judge(1250);
        assertFalse(j.onBeat());
        assertEquals(250, Math.abs(j.offsetMs()), 1e-9);
    }

    @Test
    @DisplayName("早押し・遅押しで offset の符号が変わる")
    void earlyAndLateSign() {
        assertTrue(clock().judge(1960).offsetMs() < 0);
        assertTrue(clock().judge(2040).offsetMs() > 0);
    }

    @Test
    @DisplayName("基準時刻より前（負の相対時刻）でも判定できる")
    void beforeAnchor() {
        BeatClock c = new BeatClock(120, 10_000, 100, true);
        assertTrue(c.judge(9_500 + 30).onBeat());
        assertEquals(-1, c.judge(9_500).beatIndex());
    }

    @Test
    @DisplayName("BPM 変更：直前の拍を基準に新しい間隔になる。拍番号は連続")
    void bpmChangeReanchors() {
        BeatClock c = clock();
        // 1700ms 時点で 120→60 BPM。直前の拍は 1500ms（#3）
        c.setBpm(60, 1700);
        assertEquals(1000, c.intervalMs(), 1e-9);
        assertEquals(1500, c.anchorMs(), 1e-9);
        assertEquals(3, c.anchorIndex());
        // 旧テンポなら 2000ms が拍だったが、新テンポでは 2500ms が次の拍
        assertFalse(c.judge(2000).onBeat());
        BeatClock.Judgement j = c.judge(2500);
        assertTrue(j.onBeat());
        assertEquals(4, j.beatIndex());
        // 新テンポでも猶予の境界は同じ
        assertTrue(c.judge(3500 + 100).onBeat());
        assertFalse(c.judge(3500 + 101).onBeat());
    }

    @Test
    @DisplayName("BPM 変更：割り切れない BPM でも誤差が溜まらない")
    void nonIntegerInterval() {
        BeatClock c = new BeatClock(128, 0, 100, true); // 468.75ms
        assertTrue(c.judge(468.75 * 1000).onBeat());
        assertEquals(1000, c.judge(468.75 * 1000).beatIndex());
        c.setBpm(140, 468.75 * 1000 + 10);
        assertTrue(c.judge(468.75 * 1000 + 60_000.0 / 140 * 7).onBeat());
    }

    @Test
    @DisplayName("BPM 変更を拍ちょうどの時刻に行うとその拍が基準になる")
    void bpmChangeOnBeat() {
        BeatClock c = clock();
        c.setBpm(150, 1000);
        assertEquals(1000, c.anchorMs(), 1e-9);
        assertEquals(2, c.anchorIndex());
        assertTrue(c.judge(1400).onBeat());
    }

    @Test
    @DisplayName("猶予 0 は拍ちょうどのみ")
    void zeroTolerance() {
        BeatClock c = new BeatClock(120, 0, 0, true);
        assertTrue(c.judge(500).onBeat());
        assertFalse(c.judge(500.5).onBeat());
    }

    @Test
    @DisplayName("ビートを無効化すると常に猶予外（罰は無いのでボーナス無しになるだけ）")
    void disabled() {
        BeatClock c = new BeatClock(120, 0, 100, false);
        assertFalse(c.judge(500).onBeat());
        assertSame(BeatClock.Judgement.DISABLED, c.judge(500));
    }

    @Test
    void phaseWrapsEachBeat() {
        BeatClock c = clock();
        assertEquals(0.0, c.phase(1000), 1e-9);
        assertEquals(0.5, c.phase(1250), 1e-9);
        assertEquals(0.99, c.phase(1495), 1e-9);
    }

    @Test
    void invalidBpmRejected() {
        assertThrows(IllegalArgumentException.class, () -> new BeatClock(0, 0, 100, true));
        assertThrows(IllegalArgumentException.class, () -> clock().setBpm(-1, 0));
    }

    @Test
    @DisplayName("sync でクライアント側の時計を復元できる")
    void syncRestores() {
        BeatClock server = clock();
        server.setBpm(90, 2600);
        BeatClock client = new BeatClock(60, 0, 0, false);
        client.sync(server.bpm(), server.anchorMs(), server.anchorIndex(), server.toleranceMs(), server.enabled());
        assertEquals(server.judge(9_999), client.judge(9_999));
    }
}
