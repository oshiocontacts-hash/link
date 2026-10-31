package com.neonclash.core.fx;

import com.neonclash.core.math.Vec3d;

import java.util.Locale;
import java.util.Random;

/** 演出の計算に使う小さな関数。 */
public final class Fx {
    private Fx() {
    }

    /** 球面上の一様な向き。 */
    public static Vec3d sphere(Random r) {
        double y = r.nextDouble() * 2 - 1;
        double a = r.nextDouble() * Math.PI * 2;
        double h = Math.sqrt(Math.max(0, 1 - y * y));
        return new Vec3d(Math.cos(a) * h, y, Math.sin(a) * h);
    }

    /** axis の周り、半角 halfAngleDeg の円錐の中の一様な向き。axis が長さ 0 なら球面。 */
    public static Vec3d cone(Random r, Vec3d axis, double halfAngleDeg) {
        Vec3d n = axis.normalize();
        if (n.length() < 0.5) {
            return sphere(r);
        }
        double cosMax = Math.cos(Math.toRadians(Math.max(0, Math.min(180, halfAngleDeg))));
        double cos = 1 - r.nextDouble() * (1 - cosMax);
        double sin = Math.sqrt(Math.max(0, 1 - cos * cos));
        double a = r.nextDouble() * Math.PI * 2;
        Vec3d u = perpendicular(n);
        Vec3d v = cross(n, u);
        return n.scale(cos).add(u.scale(Math.cos(a) * sin)).add(v.scale(Math.sin(a) * sin)).normalize();
    }

    /** n に直交する単位ベクトルの 1 つ。 */
    public static Vec3d perpendicular(Vec3d n) {
        Vec3d helper = Math.abs(n.y()) < 0.9 ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0);
        return cross(n, helper).normalize();
    }

    public static Vec3d cross(Vec3d a, Vec3d b) {
        return new Vec3d(a.y() * b.z() - a.z() * b.y(), a.z() * b.x() - a.x() * b.z(), a.x() * b.y() - a.y() * b.x());
    }

    /** 速く動き出してゆっくり止まる 0..1。 */
    public static float easeOut(float t) {
        float u = 1f - Math.max(0f, Math.min(1f, t));
        return 1f - u * u * u;
    }

    /** 色を白へ寄せる。amount 0..1。 */
    public static int whiten(int rgb, float amount) {
        float a = Math.max(0f, Math.min(1f, amount));
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        return Math.round(r + (255 - r) * a) << 16 | Math.round(g + (255 - g) * a) << 8 | Math.round(b + (255 - b) * a);
    }

    /** ダメージ表示の文字列。整数はそのまま、端数は小数 1 桁。 */
    public static String damageText(float amount) {
        float rounded = Math.round(amount * 10f) / 10f;
        return rounded == Math.rint(rounded) ? Integer.toString(Math.round(rounded)) : String.format(Locale.ROOT, "%.1f", rounded);
    }

    /** 画面上の向き（ラジアン、0 が正面、時計回り）。yaw は Minecraft の向き（度）。 */
    public static double screenAngle(double worldAngleDeg, double yawDeg) {
        double a = Math.toRadians(worldAngleDeg - yawDeg - 90);
        return Math.atan2(Math.sin(a), Math.cos(a));
    }
}
