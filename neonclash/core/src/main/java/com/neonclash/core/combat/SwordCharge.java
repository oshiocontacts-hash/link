package com.neonclash.core.combat;

/**
 * 剣の「溜め → 斬撃 → 硬直」の状態機械。
 * <ul>
 *   <li>{@link #startWindup} で溜め開始。溜め中に {@link #interrupt()}（ハンドガン被弾）されると溜めはリセットされる。</li>
 *   <li>溜めが満ちた tick に {@link #tick()} が true を返す。その tick に当たり判定を行う。</li>
 *   <li>斬撃後は硬直（recovery）に入り、その間は次の溜めを始められない。</li>
 * </ul>
 */
public final class SwordCharge {
    public enum State { IDLE, WINDUP, RECOVERY, LOCKOUT }

    private State state = State.IDLE;
    private int timer;
    private int windupTotal;
    private int recoveryTicks;
    private int interruptLockoutTicks;
    private int interrupts;

    /**
     * @param windupTicks           溜め時間
     * @param recoveryTicks         斬撃後の硬直
     * @param interruptLockoutTicks 溜めを潰された後、再び溜め始められるまでの時間（0 で即再開可）
     * @return 溜めを開始できたか
     */
    public boolean startWindup(int windupTicks, int recoveryTicks, int interruptLockoutTicks) {
        if (state != State.IDLE) {
            return false;
        }
        this.windupTotal = Math.max(1, windupTicks);
        this.timer = windupTotal;
        this.recoveryTicks = Math.max(0, recoveryTicks);
        this.interruptLockoutTicks = Math.max(0, interruptLockoutTicks);
        this.state = State.WINDUP;
        return true;
    }

    /** @return この tick で斬撃が発生するなら true */
    public boolean tick() {
        switch (state) {
            case WINDUP -> {
                if (--timer <= 0) {
                    enter(State.RECOVERY, recoveryTicks);
                    return true;
                }
            }
            case RECOVERY, LOCKOUT -> {
                if (--timer <= 0) {
                    enter(State.IDLE, 0);
                }
            }
            case IDLE -> {
            }
        }
        return false;
    }

    /** ハンドガンで撃たれた。溜め中なら溜めをリセットして true。 */
    public boolean interrupt() {
        if (state != State.WINDUP) {
            return false;
        }
        interrupts++;
        enter(interruptLockoutTicks > 0 ? State.LOCKOUT : State.IDLE, interruptLockoutTicks);
        return true;
    }

    /** ラウンド開始・死亡時など。 */
    public void reset() {
        enter(State.IDLE, 0);
    }

    private void enter(State next, int ticks) {
        // 長さ 0 の硬直・ロックアウトは飛ばして即 IDLE
        state = (next != State.IDLE && ticks <= 0) ? State.IDLE : next;
        timer = state == State.IDLE ? 0 : ticks;
    }

    public State state() {
        return state;
    }

    public boolean isWindingUp() {
        return state == State.WINDUP;
    }

    /** 溜めの進捗 0..1（溜め中以外は 0）。 */
    public float windupProgress() {
        return state == State.WINDUP ? 1f - (float) timer / windupTotal : 0f;
    }

    public int interruptCount() {
        return interrupts;
    }
}
