package com.neonclash.core.fx;

import com.neonclash.core.math.Vec3d;

/** 飛び散る光の粒 1 個。物理だけを持ち、描画は持たない。 */
public final class Spark {
    /**
     * @param life    消えるまでの tick
     * @param size    大きさ（ブロック）
     * @param color   0xRRGGBB
     * @param drag    1 tick ごとに速度へ掛ける倍率
     * @param gravity 1 tick ごとに下へ足す速度
     * @param shard   破片（回転する板）として描くなら true
     */
    public record Look(int life, float size, int color, double drag, double gravity, boolean shard) {
        public Look {
            if (life < 1) {
                throw new IllegalArgumentException("life must be positive");
            }
        }
    }

    private final Look look;
    private final float spin;
    private double x, y, z;
    private double lastX, lastY, lastZ;
    private double vx, vy, vz;
    private int age;

    public Spark(Vec3d pos, Vec3d velocity, Look look, float spin) {
        this.look = look;
        this.spin = spin;
        x = lastX = pos.x();
        y = lastY = pos.y();
        z = lastZ = pos.z();
        vx = velocity.x();
        vy = velocity.y();
        vz = velocity.z();
    }

    void tick() {
        lastX = x;
        lastY = y;
        lastZ = z;
        x += vx;
        y += vy;
        z += vz;
        vx *= look.drag();
        vy = vy * look.drag() - look.gravity();
        vz *= look.drag();
        age++;
    }

    boolean dead() {
        return age >= look.life();
    }

    public Look look() {
        return look;
    }

    /** 破片の回転の向きと速さ（ラジアン/tick）。 */
    public float spin() {
        return spin;
    }

    public int age() {
        return age;
    }

    /** tick の間を補間した位置。 */
    public Vec3d position(float partial) {
        return new Vec3d(lastX + (x - lastX) * partial, lastY + (y - lastY) * partial, lastZ + (z - lastZ) * partial);
    }

    public Vec3d velocity() {
        return new Vec3d(vx, vy, vz);
    }

    /** 明るさ 1..0。後半で一気に落とす（線形だと消え際がだらける）。 */
    public float brightness(float partial) {
        float t = Math.min(1f, (age + partial) / look.life());
        return 1f - t * t;
    }
}
