package com.neonclash.game;

import com.neonclash.config.NCConfig;
import com.neonclash.core.style.StyleEvent;
import com.neonclash.core.upgrade.UpgradeStat;
import com.neonclash.network.NCNetwork;
import com.neonclash.network.S2CFeedbackPacket;
import com.neonclash.registry.NCSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** 通常スキル（パリィ）と切り札（ラストスタンド）。 */
public final class SkillService {
    private SkillService() {
    }

    public static boolean tryParry(Combatant c) {
        NCConfig.Server cfg = NCConfig.SERVER;
        int extra = (int) Math.round(c.upgrades.get(UpgradeStat.PARRY_WINDOW));
        if (!c.parry.tryActivate(cfg.parrySettings(extra), 1.0)) {
            return false;
        }
        LivingEntity e = c.entity();
        e.level().playSound(null, e.getX(), e.getY(), e.getZ(), NCSounds.PARRY.get(), SoundSource.PLAYERS, 0.8f, 1.3f);
        NCNetwork.send(e, new S2CFeedbackPacket(S2CFeedbackPacket.Kind.PARRY, 0, cfg.parryWindowTicks.get() + extra, 0));
        return true;
    }

    /**
     * 攻撃を受けた瞬間に呼ぶ。パリィ受付中なら攻撃を無効化し、相手を短時間スロー、自分の周りに範囲スローを張る。
     * @return 無効化したら true
     */
    public static boolean onIncomingAttack(Combatant victim, @Nullable LivingEntity attacker) {
        NCConfig.Server cfg = NCConfig.SERVER;
        int extra = (int) Math.round(victim.upgrades.get(UpgradeStat.PARRY_WINDOW));
        if (!victim.parry.tryParry(cfg.parrySettings(extra))) {
            return false;
        }
        LivingEntity e = victim.entity();
        if (attacker != null && attacker != e) {
            SlowService.slowEntity(attacker, cfg.parrySlowFactor.get(), cfg.parrySlowTicks.get());
            Combatant ac = Combatants.get(attacker);
            if (ac != null) {
                SwordService.interrupt(ac);
            }
        }
        if (e.level() instanceof ServerLevel level) {
            SlowService.addZone(level, e.position().add(0, e.getBbHeight() * 0.5, 0), cfg.parryZoneRadius.get(),
                    cfg.parryZoneFactor.get(), cfg.parryZoneTicks.get());
            HitFeedback.burst(level, e.position().add(0, e.getBbHeight() * 0.6, 0), victim.teamColor(), cfg.hitParticleCount.get());
            Vec3 center = e.position().add(0, e.getBbHeight() * 0.5, 0);
            FxService.parry(level, center, cfg.parryZoneRadius.get(), victim.teamColor());
            HitFeedback.shockwave(level, center, cfg.parryZoneRadius.get(), cfg.parryKnockback.get(), cfg.parryKnockbackLift.get(),
                    t -> t != e && SwordService.isEnemy(victim, t));
        }
        e.level().playSound(null, e.getX(), e.getY(), e.getZ(), NCSounds.PARRY_SUCCESS.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        NCNetwork.send(e, S2CFeedbackPacket.of(S2CFeedbackPacket.Kind.PARRY_SUCCESS));
        StyleService.apply(victim, StyleEvent.PARRY);
        return true;
    }

    /**
     * 致命傷を受けた瞬間に呼ぶ。まだ使っていなければ踏みとどまり、自分以外を {@code lastStandTicks} スロー。
     * @return 発動したら true（死亡をキャンセルする）
     */
    public static boolean tryLastStand(Combatant victim) {
        if (!victim.lastStand.tryTrigger()) {
            return false;
        }
        NCConfig.Server cfg = NCConfig.SERVER;
        LivingEntity e = victim.entity();
        float hp = (float) (cfg.lastStandHealth.get() + victim.upgrades.get(UpgradeStat.LAST_STAND_HEALTH));
        e.setHealth(Math.min(e.getMaxHealth(), hp));
        e.invulnerableTime = 0;
        victim.sword.reset();
        int ticks = cfg.lastStandSlowTicks.get() + (int) Math.round(victim.upgrades.get(UpgradeStat.LAST_STAND_DURATION));
        if (e.level() instanceof ServerLevel level) {
            SlowService.slowAllExcept(level, e, cfg.arenaRadius.get() * 2, cfg.lastStandSlowFactor.get(), ticks);
            HitFeedback.burst(level, e.position().add(0, e.getBbHeight() * 0.5, 0), victim.teamColor(), cfg.killParticleCount.get());
            FxService.lastStand(level, e, victim.teamColor());
        }
        e.level().playSound(null, e.getX(), e.getY(), e.getZ(), NCSounds.LAST_STAND.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        NCNetwork.send(e, new S2CFeedbackPacket(S2CFeedbackPacket.Kind.LAST_STAND, 0, ticks, 0));
        return true;
    }
}
