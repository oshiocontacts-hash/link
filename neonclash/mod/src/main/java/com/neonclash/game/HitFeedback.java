package com.neonclash.game;

import com.neonclash.config.NCConfig;
import com.neonclash.network.NCNetwork;
import com.neonclash.network.S2CFeedbackPacket;
import com.neonclash.network.S2CFeedbackPacket.Kind;
import com.neonclash.registry.NCSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * 手応え：ヒットストップ、カメラの揺れ、命中音/キル音（別の音）、チームカラーのパーティクル。
 */
public final class HitFeedback {
    private HitFeedback() {
    }

    /** 命中（キル以外）。 */
    public static void hit(@Nullable LivingEntity attacker, LivingEntity victim, Vec3 at) {
        NCConfig.Server cfg = NCConfig.SERVER;
        Combatant vc = Combatants.get(victim);
        if (vc != null) {
            vc.hitStopTicks = Math.max(vc.hitStopTicks, cfg.hitStopTicks.get());
        }
        NCNetwork.send(victim, new S2CFeedbackPacket(Kind.HIT_STOP, 0, cfg.hitStopTicks.get(), 0));
        int color = colorOf(attacker);
        if (victim.level() instanceof ServerLevel level) {
            burst(level, at, color, cfg.hitParticleCount.get());
        }
        if (attacker != null) {
            NCNetwork.send(attacker, new S2CFeedbackPacket(Kind.SHAKE, cfg.hitShakeDegrees.get().floatValue(), cfg.shakeTicks.get(), 0));
            NCNetwork.send(attacker, S2CFeedbackPacket.of(Kind.HIT_CONFIRM));
            playTo(attacker, NCSounds.HIT.get(), cfg.hitSoundVolume.get().floatValue(), cfg.hitSoundPitch.get().floatValue());
        }
    }

    /** キル。連続キルでキル音のピッチが上がる。 */
    public static void kill(@Nullable LivingEntity attacker, LivingEntity victim, Vec3 at) {
        NCConfig.Server cfg = NCConfig.SERVER;
        int color = colorOf(attacker);
        if (victim.level() instanceof ServerLevel level) {
            burst(level, at, color, cfg.killParticleCount.get());
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

    private static int colorOf(@Nullable LivingEntity attacker) {
        Combatant c = Combatants.get(attacker);
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
        // プレイヤーの移動はクライアント側で止める（S2C HIT_STOP）。モブはサーバーで止める
        LivingEntity e = c.entity();
        if (c.hitStopTicks > 0 && !(e instanceof Player)) {
            e.setDeltaMovement(Vec3.ZERO);
            if (e instanceof Mob mob) {
                mob.getNavigation().stop();
            }
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

    public static void trail(ServerLevel level, Vec3 from, Vec3 to, int rgb) {
        Vec3 d = to.subtract(from);
        int n = Math.max(1, (int) (d.length() * 4));
        DustParticleOptions p = dust(rgb);
        for (int i = 0; i <= n; i++) {
            Vec3 at = from.add(d.scale((double) i / n));
            level.sendParticles(p, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    public static void slashArc(ServerLevel level, Vec3 eye, Vec3 look, double range, int rgb) {
        DustParticleOptions p = dust(rgb);
        Vec3 side = look.cross(new Vec3(0, 1, 0)).normalize();
        int n = 9;
        for (int i = 0; i < n; i++) {
            double t = (i / (double) (n - 1)) * 2 - 1; // -1..1
            Vec3 at = eye.add(look.scale(range * 0.7)).add(side.scale(t * range * 0.5)).add(0, -0.2 * t, 0);
            level.sendParticles(p, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }
}
