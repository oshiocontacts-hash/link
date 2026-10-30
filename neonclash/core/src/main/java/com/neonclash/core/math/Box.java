package com.neonclash.core.math;

/** 軸平行な当たり判定ボックス。 */
public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

    /** 足元中心 {@code feet} に幅 {@code width}・高さ {@code height} のエンティティ箱を作る。 */
    public static Box ofEntity(Vec3d feet, double width, double height) {
        double h = width / 2.0;
        return new Box(feet.x() - h, feet.y(), feet.z() - h, feet.x() + h, feet.y() + height, feet.z() + h);
    }

    public boolean intersects(Box o) {
        return minX < o.maxX && maxX > o.minX && minY < o.maxY && maxY > o.minY && minZ < o.maxZ && maxZ > o.minZ;
    }
}
