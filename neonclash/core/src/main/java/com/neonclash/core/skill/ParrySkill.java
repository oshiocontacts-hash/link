package com.neonclash.core.skill;

/**
 * パリィ。発動から {@code windowTicks} の間に受けた攻撃を無効化し、成功を通知する。
 * 成功時はクールダウンを {@code successCooldownMultiplier} 倍にする（上手く取れたら早く次が撃てる）。
 */
public final class ParrySkill {
    public record Settings(int windowTicks, int cooldownTicks, double successCooldownMultiplier) {
    }

    private int windowLeft;
    private final Cooldown cooldown = new Cooldown();
    private boolean succeededThisWindow;

    public boolean tryActivate(Settings s, double cooldownMultiplier) {
        if (!cooldown.ready() || windowLeft > 0) {
            return false;
        }
        windowLeft = Math.max(1, s.windowTicks());
        succeededThisWindow = false;
        cooldown.start((int) Math.round(s.cooldownTicks() * cooldownMultiplier));
        return true;
    }

    public boolean isActive() {
        return windowLeft > 0;
    }

    /** 攻撃を受けた。パリィ受付中なら true（＝その攻撃は無効化する）。 */
    public boolean tryParry(Settings s) {
        if (windowLeft <= 0) {
            return false;
        }
        if (!succeededThisWindow) {
            succeededThisWindow = true;
            cooldown.start((int) Math.round(cooldown.remaining() * s.successCooldownMultiplier()));
        }
        return true;
    }

    public void tick() {
        if (windowLeft > 0) {
            windowLeft--;
        }
        cooldown.tick();
    }

    public void reset() {
        windowLeft = 0;
        succeededThisWindow = false;
        cooldown.clear();
    }

    public Cooldown cooldown() {
        return cooldown;
    }
}
