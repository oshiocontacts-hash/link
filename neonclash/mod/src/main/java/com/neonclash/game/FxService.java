package com.neonclash.game;

import com.neonclash.config.NCConfig;
import com.neonclash.network.NCNetwork;
import com.neonclash.network.S2CFxPacket;
import com.neonclash.network.S2CFxPacket.Kind;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** 世界の中の演出を近くのプレイヤーへ送る。 */
public final class FxService {
    private FxService() {
    }

    private static void near(ServerLevel level, Kind kind, Vec3 at, Vec3 dir, int color, float value, int extra) {
        NCNetwork.sendNear(level, at, NCConfig.SERVER.fxViewDistance.get(), packet(kind, at, dir, color, value, extra));
    }

    private static S2CFxPacket packet(Kind kind, Vec3 at, Vec3 dir, int color, float value, int extra) {
        return new S2CFxPacket(kind, at.x, at.y, at.z, (float) dir.x, (float) dir.y, (float) dir.z, color, value, extra);
    }

    public static void muzzle(ServerLevel level, LivingEntity shooter, Vec3 dir, int color, boolean beat) {
        near(level, Kind.MUZZLE, shooter.getEyePosition(), dir, color, beat ? 1f : 0f, shooter.getId());
    }

    public static void hit(ServerLevel level, Vec3 at, Vec3 dir, int color, int flags) {
        near(level, Kind.HIT, at, dir, color, 0f, flags);
    }

    public static void impact(ServerLevel level, Vec3 at, Vec3 normal, int color) {
        near(level, Kind.IMPACT, at, normal, color, 0f, 0);
    }

    public static void kill(ServerLevel level, Vec3 at, Vec3 dir, int color, int victimColor) {
        near(level, Kind.KILL, at, dir, color, 0f, victimColor);
    }

    public static void slash(ServerLevel level, Vec3 eye, Vec3 look, double range, int color) {
        near(level, Kind.SLASH, eye, look, color, (float) range, 0);
    }

    public static void blink(ServerLevel level, Vec3 from, Vec3 to, float height, int color) {
        near(level, Kind.BLINK, from, to.subtract(from), color, height, 0);
    }

    public static void parry(ServerLevel level, Vec3 at, double radius, int color) {
        near(level, Kind.PARRY, at, Vec3.ZERO, color, (float) radius, 0);
    }

    public static void lastStand(ServerLevel level, LivingEntity e, int color) {
        near(level, Kind.LAST_STAND, e.position(), Vec3.ZERO, color, e.getBbHeight(), 0);
    }

    public static void windup(ServerLevel level, LivingEntity e, int color, int ticks) {
        near(level, Kind.WINDUP, e.position(), Vec3.ZERO, color, ticks, e.getId());
    }

    /** 当てた本人にだけ見えるダメージの数字。 */
    public static void damageNumber(LivingEntity attacker, Vec3 at, float amount, int color, int flags) {
        NCNetwork.send(attacker, packet(Kind.DAMAGE, at, Vec3.ZERO, color, amount, flags));
    }
}
