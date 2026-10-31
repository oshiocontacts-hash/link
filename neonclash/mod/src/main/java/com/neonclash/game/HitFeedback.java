package com.neonclash.game;

import com.neonclash.config.NCConfig;
import com.neonclash.core.combat.Knockback;
import com.neonclash.network.NCNetwork;
import com.neonclash.network.S2CFeedbackPacket;
import com.neonclash.network.S2CFeedbackPacket.Kind;
import com.neonclash.network.S2CFxPacket;
import com.neonclash.registry.NCSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.function.Predicate;

/**
 * 手応え：ヒットストップ、ノックバック、カメラの揺れ、命中音/キル音（別の音）、チームカラーの演出。
 */
public final class HitFeedback {
    private HitFeedback() {
    }

    /**
     * 命中（キル以外）。
     * @param dir   弾の進む向き
     * @param flags {@link S2CFxPacket#BEAT} {@link S2CFxPacket#HEAD}
     */
    public static void hit(@Nullable LivingEntity attacker, LivingEntity victim, Vec3 at, Vec3 dir, int flags) {
        NCConfig.Server cfg = NCConfig.SERVER;
        Combatant vc = Combatants.get(victim);
        if (vc != null) {
            vc.hitStopTicks = Math.max(vc.hitStopTicks, cfg.hitStopTicks.get());
        }
        NCNetwork.send(victim, new S2CFeedbackPacket(Kind.HIT_STOP, 0, cfg.hitStopTicks.get(), 0));
        int color = colorOf(attacker);
        if (victim.level() instanceof ServerLevel level) {
            burst(level, at, color, cfg.hitParticleCount.get());
            FxService.hit(level, at, dir, color, flags);
        }
        double push = cfg.gunKnockback.get();
        if ((flags & S2CFxPacket.BEAT) != 0) push *= cfg.knockbackBeatMultiplier.get();
        if ((flags & S2CFxPacket.HEAD) != 0) push *= cfg.knockbackHeadMultiplier.get();
        knockback(victim, dir, push, cfg.gunKnockbackLift.get());
        hurtFeedback(victim, attacker);
        if (attacker != null) {
            NCNetwork.send(attacker, new S2CFeedbackPacket(Kind.SHAKE, cfg.hitShakeDegrees.get().floatValue(), cfg.shakeTicks.get(), 0));
            NCNetwork.send(attacker, new S2CFeedbackPacket(Kind.HIT_CONFIRM, 0, 0, flags));
            Combatant ac = Combatants.get(attacker);
            float pitch = ac != null ? ac.hitCombo.onKill(attacker.level().getGameTime(), cfg.hitComboSettings()) : cfg.hitSoundPitch.get().floatValue();
            SoundEvent sound = (flags & S2CFxPacket.HEAD) != 0 ? NCSounds.HIT_HEAD.get() : NCSounds.HIT.get();
            playTo(attacker, sound, cfg.hitSoundVolume.get().floatValue(), pitch);
        }
    }

    /** 撃たれた側：どこから撃たれたかの表示、揺れ、鈍い音。 */
    private static void hurtFeedback(LivingEntity victim, @Nullable LivingEntity attacker) {
        NCConfig.Server cfg = NCConfig.SERVER;
        NCNetwork.send(victim, new S2CFeedbackPacket(Kind.SHAKE, cfg.hurtShakeDegrees.get().floatValue(), cfg.shakeTicks.get(), 0));
        if (attacker != null && attacker != victim) {
            float angle = (float) Math.toDegrees(Mth.atan2(attacker.getZ() - victim.getZ(), attacker.getX() - victim.getX()));
            NCNetwork.send(victim, new S2CFeedbackPacket(Kind.HURT, angle, 0, 0));
        }
        playTo(victim, NCSounds.HURT.get(), cfg.hurtSoundVolume.get().floatValue(), 1.0f);
    }

    /** キル。連続キルでキル音のピッチが上がる。倒された相手は砕け、近くの人を爆風で押す。 */
    public static void kill(@Nullable LivingEntity attacker, LivingEntity victim, Vec3 at, Vec3 dir) {
        NCConfig.Server cfg = NCConfig.SERVER;
        int color = colorOf(attacker);
        if (victim.level() instanceof ServerLevel level) {
            burst(level, at, color, cfg.killParticleCount.get());
            FxService.kill(level, at, dir, color, colorOf(victim));
            level.playSound(null, at.x, at.y, at.z, NCSounds.SHATTER.get(), SoundSource.PLAYERS, 1.2f, 1.0f);
            shockwave(level, at, cfg.killBlastRadius.get(), cfg.killBlastKnockback.get(), cfg.killBlastLift.get(),
                    t -> t != victim && t != attacker && Combatants.get(t) != null && !Combatants.get(t).eliminated);
        }
        if (attacker == null) {
            return;
        }
        Combatant ac = Combatants.get(attacker);
        float pitch = cfg.killBasePitch.get().floatValue();
        int streak = 1;
        if (ac != null) {
            pitch = ac.killStreak.onKill(attacker.level().getGameTime(), cfg.killStreakSettings());
            streak = ac.killStreak.streak();
        }
        NCNetwork.send(attacker, new S2CFeedbackPacket(Kind.SHAKE, cfg.killShakeDegrees.get().floatValue(), cfg.shakeTicks.get(), 0));
        NCNetwork.send(attacker, new S2CFeedbackPacket(Kind.KILL_CONFIRM, pitch, 0, streak));
        playTo(attacker, NCSounds.KILL.get(), cfg.killSoundVolume.get().floatValue(), pitch);
    }

    /** 当たった向き。弾が止まっていれば攻撃側から見た向き。 */
    public static Vec3 direction(@Nullable Entity attacker, Entity victim, @Nullable Entity bullet) {
        if (bullet != null && bullet.getDeltaMovement().lengthSqr() > 1.0e-6) {
            return bullet.getDeltaMovement().normalize();
        }
        return attacker != null ? victim.position().subtract(attacker.position()).normalize() : Vec3.ZERO;
    }

    /** 押す。ヒットストップは入力だけを止めるので、押された勢いは消えない。 */
    public static void knockback(LivingEntity e, Vec3 dir, double strength, double lift) {
        if (strength <= 0 && lift <= 0) return;
        double resistance = e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        e.setDeltaMovement(Vecs.mc(Knockback.apply(Vecs.core(e.getDeltaMovement()),
                Knockback.impulse(Vecs.core(dir), strength, lift, resistance), NCConfig.SERVER.knockbackMaxSpeed.get())));
        e.hasImpulse = true;
        e.hurtMarked = true; // プレイヤーへ速度を送る
    }

    /** center から外へ押す。 */
    public static void shockwave(ServerLevel level, Vec3 center, double radius, double strength, double lift, Predicate<LivingEntity> who) {
        if (radius <= 0) return;
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                t -> t.isAlive() && !t.isSpectator() && who.test(t))) {
            if (t.position().distanceToSqr(center) <= radius * radius) {
                knockback(t, t.position().subtract(center), strength, lift);
            }
        }
    }

    public static int colorOf(@Nullable LivingEntity e) {
        Combatant c = Combatants.get(e);
        if (c != null) {
            return c.teamColor();
        }
        return NCConfig.parseColor(NCConfig.SERVER.neutralColor.get(), 0xF8F32B);
    }

    /** プレイヤーなら本人にだけ、それ以外はその場で鳴らす。 */
    public static void playTo(LivingEntity who, SoundEvent sound, float volume, float pitch) {
        if (who instanceof ServerPlayer sp) {
            sp.playNotifySound(sound, SoundSource.PLAYERS, volume, pitch);
        } else {
            who.level().playSound(null, who.getX(), who.getY(), who.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
        }
    }

    static void tickHitStop(Combatant c) {
        // プレイヤーの入力はクライアント側で止める（S2C HIT_STOP）。モブは経路を止める。速度は残す（ノックバックが消えないように）
        LivingEntity e = c.entity();
        if (c.hitStopTicks > 0 && !(e instanceof Player) && e instanceof Mob mob) {
            mob.getNavigation().stop();
        }
    }

    public static DustParticleOptions dust(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        return new DustParticleOptions(new Vector3f(r, g, b), NCConfig.SERVER.particleScale.get().floatValue());
    }

    public static void burst(ServerLevel level, Vec3 at, int rgb, int count) {
        if (count <= 0) return;
        double s = NCConfig.SERVER.particleSpread.get();
        level.sendParticles(dust(rgb), at.x, at.y, at.z, count, s, s, s, 0.0);
    }
}
