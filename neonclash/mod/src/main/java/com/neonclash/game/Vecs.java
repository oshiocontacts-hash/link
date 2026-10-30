package com.neonclash.game;

import com.neonclash.core.math.Box;
import com.neonclash.core.math.Vec3d;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** core と Minecraft の型変換。 */
public final class Vecs {
    private Vecs() {
    }

    public static Vec3d core(Vec3 v) {
        return new Vec3d(v.x, v.y, v.z);
    }

    public static Vec3 mc(Vec3d v) {
        return new Vec3(v.x(), v.y(), v.z());
    }

    public static AABB mc(Box b) {
        return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
    }
}
