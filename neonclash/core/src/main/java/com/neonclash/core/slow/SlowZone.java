package com.neonclash.core.slow;

import com.neonclash.core.math.Vec3d;

/** 範囲スロー。球の内側にいるエンティティ・弾の速度を {@code factor} 倍にする。 */
public record SlowZone(Vec3d center, double radius, double factor, long expiresAtTick) {
    public boolean contains(Vec3d p) {
        return p.distanceTo(center) <= radius;
    }

    public boolean expired(long nowTick) {
        return nowTick >= expiresAtTick;
    }
}
