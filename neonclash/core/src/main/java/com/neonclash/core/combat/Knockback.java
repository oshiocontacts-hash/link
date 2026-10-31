package com.neonclash.core.combat;

import com.neonclash.core.math.Vec3d;

/** ノックバックの計算。向きは水平成分だけを使い、持ち上げは別に足す（真上や真下から撃たれても押される量が変わらない）。 */
public final class Knockback {
    private Knockback() {
    }

    /**
     * 与える速度。
     * @param direction  押す向き（長さは問わない）。水平成分が無ければ持ち上げだけになる
     * @param strength   水平方向の速さ（ブロック/tick）
     * @param lift       上向きの速さ
     * @param resistance ノックバック耐性 0..1（1 なら動かない）
     */
    public static Vec3d impulse(Vec3d direction, double strength, double lift, double resistance) {
        double keep = 1.0 - Math.max(0.0, Math.min(1.0, resistance));
        Vec3d flat = new Vec3d(direction.x(), 0, direction.z()).normalize();
        return new Vec3d(flat.x() * strength * keep, lift * keep, flat.z() * strength * keep);
    }

    /**
     * 今の速度に足す。水平の速さは {@code maxSpeed} で頭打ちにする（連射が積み重なって飛びすぎないように）。
     * 上向きの力が入るときは落下を打ち消す（空中で撃たれても持ち上がる）。
     */
    public static Vec3d apply(Vec3d velocity, Vec3d impulse, double maxSpeed) {
        double x = velocity.x() + impulse.x();
        double z = velocity.z() + impulse.z();
        double speed = Math.sqrt(x * x + z * z);
        if (speed > maxSpeed && speed > 1.0e-9) {
            x *= maxSpeed / speed;
            z *= maxSpeed / speed;
        }
        double y = impulse.y() > 0 ? Math.min(maxSpeed, Math.max(velocity.y(), 0) + impulse.y()) : velocity.y();
        return new Vec3d(x, y, z);
    }
}
