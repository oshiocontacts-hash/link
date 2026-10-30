package com.neonclash.core.round;

import com.neonclash.core.round.RoundEngine.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RoundEngineTest {
    // 90 秒 = 1800 tick、60 秒 = 1200 tick で縮小、ラウンド間 5 秒 = 100 tick、3 本先取
    static final Settings S = new Settings(1800, 1200, 100, 3, 9);

    final UUID r1 = UUID.randomUUID(), r2 = UUID.randomUUID(), b1 = UUID.randomUUID(), b2 = UUID.randomUUID();
    final Map<UUID, Double> health = new HashMap<>();
    RoundEngine engine;

    @BeforeEach
    void setUp() {
        health.clear();
        engine = new RoundEngine(S, (team, alive) -> alive.stream().mapToDouble(id -> health.getOrDefault(id, 1.0)).sum());
        Map<UUID, Team> roster = new LinkedHashMap<>();
        roster.put(r1, Team.RED);
        roster.put(r2, Team.RED);
        roster.put(b1, Team.BLUE);
        roster.put(b2, Team.BLUE);
        List<Event> ev = engine.start(roster);
        assertEquals(List.of(new RoundStarted(1)), ev);
    }

    private List<Event> tickN(int n) {
        List<Event> all = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) all.addAll(engine.tick());
        return all;
    }

    private void skipIntermission() {
        List<Event> ev = tickN(S.intermissionTicks());
        assertTrue(ev.stream().anyMatch(e -> e instanceof RoundStarted), "next round should start: " + ev);
    }

    @Test
    @DisplayName("60 秒経過で縮小開始イベント")
    void shrinkStartsAt60s() {
        List<Event> ev = tickN(1199);
        assertTrue(ev.isEmpty());
        assertFalse(engine.isShrinking());
        ev = engine.tick();
        assertEquals(List.of(new ShrinkStarted(1)), ev);
        assertTrue(engine.isShrinking());
    }

    @Test
    @DisplayName("全滅させたチームが勝ち、決着キルの脱落者が記録される")
    void eliminationWins() {
        assertTrue(engine.eliminate(b1).isEmpty());
        List<Event> ev = engine.eliminate(b2);
        RoundEnded end = (RoundEnded) ev.get(0);
        assertEquals(Team.RED, end.winner());
        assertEquals(EndReason.ELIMINATION, end.reason());
        assertEquals(java.util.Set.of(b2), end.decisivePlayers());
        assertEquals(1, engine.score(Team.RED));
        assertEquals(Phase.INTERMISSION, engine.phase());
    }

    @Test
    @DisplayName("時間切れ：生存人数が多い方が勝ち")
    void timeoutMoreAliveWins() {
        engine.eliminate(r1);
        List<Event> ev = tickN(1800);
        RoundEnded end = (RoundEnded) ev.stream().filter(e -> e instanceof RoundEnded).findFirst().orElseThrow();
        assertEquals(Team.BLUE, end.winner());
        assertEquals(EndReason.TIMEOUT, end.reason());
        assertTrue(end.decisivePlayers().isEmpty());
    }

    @Test
    @DisplayName("時間切れで同数：体力合計が多い方が勝ち")
    void timeoutTieBrokenByHealth() {
        health.put(r1, 0.4);
        health.put(r2, 0.4);
        List<Event> ev = tickN(1800);
        RoundEnded end = (RoundEnded) ev.stream().filter(e -> e instanceof RoundEnded).findFirst().orElseThrow();
        assertEquals(Team.BLUE, end.winner());
    }

    @Test
    @DisplayName("時間切れで同数・同体力 → 引き分け（得点なし、次ラウンドへ）")
    void timeoutDraw() {
        List<Event> ev = tickN(1800);
        RoundEnded end = (RoundEnded) ev.stream().filter(e -> e instanceof RoundEnded).findFirst().orElseThrow();
        assertTrue(end.isDraw());
        assertEquals(0, engine.score(Team.RED));
        assertEquals(0, engine.score(Team.BLUE));
        assertEquals(Phase.INTERMISSION, engine.phase());
        skipIntermission();
        assertEquals(2, engine.round());
    }

    @Test
    @DisplayName("同時全滅は引き分け")
    void simultaneousWipeIsDraw() {
        engine.eliminate(r1);
        engine.eliminate(b1);
        List<Event> ev = engine.eliminateAll(List.of(r2, b2));
        RoundEnded end = (RoundEnded) ev.get(0);
        assertTrue(end.isDraw());
        assertEquals(EndReason.ELIMINATION, end.reason());
    }

    @Test
    @DisplayName("3 本先取で試合終了")
    void firstToThree() {
        for (int round = 1; round <= 3; round++) {
            assertEquals(round, engine.round());
            engine.eliminate(b1);
            List<Event> ev = engine.eliminate(b2);
            if (round < 3) {
                assertEquals(Phase.INTERMISSION, engine.phase());
                skipIntermission();
            } else {
                assertEquals(new MatchEnded(Team.RED, 3, 0), ev.get(1));
                assertEquals(Phase.FINISHED, engine.phase());
            }
        }
        assertTrue(engine.tick().isEmpty(), "finished match does not progress");
    }

    @Test
    @DisplayName("2-2 からでも 3 本目を取った方が勝つ")
    void twoTwoThenDecider() {
        Team[] winners = {Team.RED, Team.BLUE, Team.RED, Team.BLUE, Team.BLUE};
        List<Event> last = List.of();
        for (Team w : winners) {
            if (w == Team.RED) {
                engine.eliminate(b1);
                last = engine.eliminate(b2);
            } else {
                engine.eliminate(r1);
                last = engine.eliminate(r2);
            }
            if (engine.phase() == Phase.INTERMISSION) skipIntermission();
        }
        assertEquals(new MatchEnded(Team.BLUE, 2, 3), last.get(1));
    }

    @Test
    @DisplayName("引き分けが続いて maxRounds に達したら点の多い方、同点なら試合も引き分け")
    void maxRoundsCap() {
        RoundEngine e = new RoundEngine(new Settings(10, 5, 2, 3, 3), (t, a) -> 0);
        e.start(Map.of(r1, Team.RED, b1, Team.BLUE));
        e.eliminate(b1); // R 1-0
        for (int i = 0; i < 2; i++) e.tick();
        List<Event> all = new java.util.ArrayList<>();
        for (int i = 0; i < 10 + 2 + 10; i++) all.addAll(e.tick()); // 2 回時間切れ引き分け
        assertEquals(new MatchEnded(Team.RED, 1, 0), all.get(all.size() - 1));

        RoundEngine d = new RoundEngine(new Settings(10, 5, 2, 3, 1), (t, a) -> 0);
        d.start(Map.of(r1, Team.RED, b1, Team.BLUE));
        List<Event> ev = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) ev.addAll(d.tick());
        assertEquals(new MatchEnded(null, 0, 0), ev.get(ev.size() - 1));
    }

    @Test
    @DisplayName("ラウンドが変わると全員復活、時間もリセット")
    void newRoundResets() {
        engine.eliminate(b1);
        engine.eliminate(b2);
        skipIntermission();
        assertEquals(2, engine.aliveCount(Team.BLUE));
        assertEquals(1800, engine.ticksRemaining());
        assertFalse(engine.isShrinking());
    }

    @Test
    @DisplayName("ラウンド外の脱落・二重脱落は無視")
    void eliminationOutsideRoundIgnored() {
        engine.eliminate(b1);
        assertTrue(engine.eliminate(b1).isEmpty());
        engine.eliminate(b2);
        assertTrue(engine.eliminate(r1).isEmpty());
        assertEquals(2, engine.aliveCount(Team.RED));
    }

    @Test
    @DisplayName("ラウンド間にチームが空になったら相手の勝ちで試合終了")
    void teamLeavesDuringIntermission() {
        engine.eliminate(b1);
        engine.eliminate(b2);
        engine.removePlayer(r1);
        List<Event> ev = engine.removePlayer(r2);
        assertEquals(new MatchEnded(Team.BLUE, 1, 0), ev.get(ev.size() - 1));
    }

    @Test
    @DisplayName("片方のチームが居なければ開始できない")
    void needsBothTeams() {
        RoundEngine e = new RoundEngine(S, (t, a) -> 0);
        assertThrows(IllegalStateException.class, () -> e.start(Map.of(r1, Team.RED)));
    }

    @Test
    @DisplayName("ArenaZone：60 秒まで一定、そこから 90 秒にかけて線形に縮む")
    void arenaShrink() {
        ArenaZone z = new ArenaZone(30, 8, 1200, 1800);
        assertEquals(30, z.radiusAt(0), 1e-9);
        assertEquals(30, z.radiusAt(1200), 1e-9);
        assertEquals(19, z.radiusAt(1500), 1e-9);
        assertEquals(8, z.radiusAt(1800), 1e-9);
        assertEquals(8, z.radiusAt(5000), 1e-9);
        assertFalse(z.isOutside(10, 10, 0));
        assertTrue(z.isOutside(10, 10, 1800));
    }
}
