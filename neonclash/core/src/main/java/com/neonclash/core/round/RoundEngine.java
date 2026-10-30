package com.neonclash.core.round;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ラウンド進行。1ラウンド {@code roundTicks}、{@code shrinkStartTick} でエリア縮小開始、{@code winsToWin} 本先取。
 * 各プレイヤーはラウンドごとに1ライフ。
 * <p>
 * 決着：
 * <ul>
 *   <li>片方のチームが全滅 → もう片方の勝ち（両方同時に全滅したら引き分け）</li>
 *   <li>時間切れ → 生存人数が多い方の勝ち。同数なら {@link TieBreaker}（体力合計など）で比較し、それも同じなら引き分け</li>
 *   <li>引き分けはどちらにも点が入らず、次のラウンドへ。{@code maxRounds} に達したら点数の多い方が勝ち、同点なら試合も引き分け</li>
 * </ul>
 */
public final class RoundEngine {

    public record Settings(int roundTicks, int shrinkStartTick, int intermissionTicks, int winsToWin, int maxRounds) {
        public Settings {
            if (roundTicks <= 0 || winsToWin <= 0) {
                throw new IllegalArgumentException("roundTicks and winsToWin must be > 0");
            }
        }
    }

    public enum Phase { IDLE, ROUND, INTERMISSION, FINISHED }

    public enum EndReason { ELIMINATION, TIMEOUT }

    public sealed interface Event permits RoundStarted, ShrinkStarted, RoundEnded, MatchEnded {
    }

    public record RoundStarted(int round) implements Event {
    }

    public record ShrinkStarted(int round) implements Event {
    }

    /**
     * @param winner          勝ったチーム。引き分けなら null
     * @param decisivePlayers このラウンドを決めた脱落者（全滅時のみ。時間切れなら空）
     */
    public record RoundEnded(int round, Team winner, EndReason reason, Set<UUID> decisivePlayers) implements Event {
        public boolean isDraw() {
            return winner == null;
        }
    }

    /** @param winner 試合の勝者。引き分けなら null */
    public record MatchEnded(Team winner, int redScore, int blueScore) implements Event {
    }

    /** 時間切れで生存人数が同じだったときの比較値（大きい方が勝ち）。 */
    @FunctionalInterface
    public interface TieBreaker {
        double score(Team team, Set<UUID> alivePlayers);
    }

    private static final double TIE_EPSILON = 1.0e-6;

    private final Settings settings;
    private final TieBreaker tieBreaker;
    private final Map<UUID, Team> roster = new LinkedHashMap<>();
    private final Set<UUID> alive = new LinkedHashSet<>();
    private final EnumMap<Team, Integer> scores = new EnumMap<>(Team.class);
    private Phase phase = Phase.IDLE;
    private int round;
    private int elapsed;
    private int intermissionLeft;
    private RoundEnded lastResult;

    public RoundEngine(Settings settings, TieBreaker tieBreaker) {
        this.settings = settings;
        this.tieBreaker = tieBreaker;
        resetScores();
    }

    private void resetScores() {
        for (Team t : Team.values()) {
            scores.put(t, 0);
        }
    }

    public List<Event> start(Map<UUID, Team> players) {
        for (Team t : Team.values()) {
            if (!players.containsValue(t)) {
                throw new IllegalStateException("team " + t + " has no players");
            }
        }
        roster.clear();
        roster.putAll(players);
        resetScores();
        round = 0;
        lastResult = null;
        List<Event> out = new ArrayList<>();
        startRound(out);
        return out;
    }

    public void stop() {
        phase = Phase.IDLE;
        alive.clear();
    }

    private void startRound(List<Event> out) {
        round++;
        elapsed = 0;
        alive.clear();
        alive.addAll(roster.keySet());
        phase = Phase.ROUND;
        out.add(new RoundStarted(round));
    }

    public List<Event> tick() {
        List<Event> out = new ArrayList<>();
        switch (phase) {
            case ROUND -> {
                elapsed++;
                if (elapsed == settings.shrinkStartTick()) {
                    out.add(new ShrinkStarted(round));
                }
                if (elapsed >= settings.roundTicks()) {
                    endByTimeout(out);
                }
            }
            case INTERMISSION -> {
                if (--intermissionLeft <= 0) {
                    if (teamEmptyInRoster(out)) {
                        break;
                    }
                    startRound(out);
                }
            }
            case IDLE, FINISHED -> {
            }
        }
        return out;
    }

    public List<Event> eliminate(UUID player) {
        return eliminateAll(List.of(player));
    }

    /** 同じ tick に脱落した複数人をまとめて処理する（同時全滅を引き分けにするため）。 */
    public List<Event> eliminateAll(Collection<UUID> players) {
        List<Event> out = new ArrayList<>();
        if (phase != Phase.ROUND) {
            return out;
        }
        Set<UUID> removed = new LinkedHashSet<>();
        for (UUID p : players) {
            if (alive.remove(p)) {
                removed.add(p);
            }
        }
        if (removed.isEmpty()) {
            return out;
        }
        int red = aliveCount(Team.RED);
        int blue = aliveCount(Team.BLUE);
        if (red == 0 && blue == 0) {
            endRound(out, null, EndReason.ELIMINATION, removed);
        } else if (red == 0) {
            endRound(out, Team.BLUE, EndReason.ELIMINATION, removed);
        } else if (blue == 0) {
            endRound(out, Team.RED, EndReason.ELIMINATION, removed);
        }
        return out;
    }

    /** 切断など。ラウンド中なら脱落扱い。 */
    public List<Event> removePlayer(UUID player) {
        List<Event> out = new ArrayList<>(eliminate(player));
        roster.remove(player);
        if (phase == Phase.INTERMISSION) {
            teamEmptyInRoster(out);
        }
        return out;
    }

    private boolean teamEmptyInRoster(List<Event> out) {
        boolean redEmpty = !roster.containsValue(Team.RED);
        boolean blueEmpty = !roster.containsValue(Team.BLUE);
        if (!redEmpty && !blueEmpty) {
            return false;
        }
        Team winner = redEmpty && blueEmpty ? null : (redEmpty ? Team.BLUE : Team.RED);
        finish(out, winner);
        return true;
    }

    private void endByTimeout(List<Event> out) {
        int red = aliveCount(Team.RED);
        int blue = aliveCount(Team.BLUE);
        Team winner;
        if (red != blue) {
            winner = red > blue ? Team.RED : Team.BLUE;
        } else {
            double r = tieBreaker.score(Team.RED, aliveOf(Team.RED));
            double b = tieBreaker.score(Team.BLUE, aliveOf(Team.BLUE));
            winner = Math.abs(r - b) <= TIE_EPSILON ? null : (r > b ? Team.RED : Team.BLUE);
        }
        endRound(out, winner, EndReason.TIMEOUT, Set.of());
    }

    private void endRound(List<Event> out, Team winner, EndReason reason, Set<UUID> decisive) {
        if (winner != null) {
            scores.merge(winner, 1, Integer::sum);
        }
        lastResult = new RoundEnded(round, winner, reason, Set.copyOf(decisive));
        out.add(lastResult);
        if (winner != null && scores.get(winner) >= settings.winsToWin()) {
            finish(out, winner);
        } else if (settings.maxRounds() > 0 && round >= settings.maxRounds()) {
            int r = scores.get(Team.RED);
            int b = scores.get(Team.BLUE);
            finish(out, r == b ? null : (r > b ? Team.RED : Team.BLUE));
        } else {
            phase = Phase.INTERMISSION;
            intermissionLeft = Math.max(1, settings.intermissionTicks());
        }
    }

    private void finish(List<Event> out, Team winner) {
        phase = Phase.FINISHED;
        out.add(new MatchEnded(winner, scores.get(Team.RED), scores.get(Team.BLUE)));
    }

    public int aliveCount(Team team) {
        return aliveOf(team).size();
    }

    public Set<UUID> aliveOf(Team team) {
        Set<UUID> s = new LinkedHashSet<>();
        for (UUID id : alive) {
            if (roster.get(id) == team) {
                s.add(id);
            }
        }
        return s;
    }

    public boolean isAlive(UUID player) {
        return alive.contains(player);
    }

    public Team teamOf(UUID player) {
        return roster.get(player);
    }

    public Map<UUID, Team> roster() {
        return Map.copyOf(roster);
    }

    public Phase phase() {
        return phase;
    }

    public int round() {
        return round;
    }

    public int elapsedTicks() {
        return elapsed;
    }

    public int ticksRemaining() {
        return phase == Phase.ROUND ? Math.max(0, settings.roundTicks() - elapsed) : 0;
    }

    public int intermissionTicksLeft() {
        return phase == Phase.INTERMISSION ? intermissionLeft : 0;
    }

    public boolean isShrinking() {
        return phase == Phase.ROUND && elapsed >= settings.shrinkStartTick();
    }

    public int score(Team team) {
        return scores.get(team);
    }

    public RoundEnded lastResult() {
        return lastResult;
    }

    public Settings settings() {
        return settings;
    }
}
