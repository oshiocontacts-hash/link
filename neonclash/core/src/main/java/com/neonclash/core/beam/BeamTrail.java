package com.neonclash.core.beam;

import com.neonclash.core.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;

/**
 * 弾の軌道に残すビーム。弾が 1 tick に進んだ区間を 1 本の区間として持ち、古いものから順に消えていく
 * （先に通った根元から消えるので、彗星の尾のように見える）。描画は持たない。
 */
public final class BeamTrail {

    /**
     * @param fadeTicks   区間が現れてから消えるまで
     * @param maxSegments 同時に持てる区間の数（超えたら古いものから捨てる）
     */
    public record Settings(int fadeTicks, int maxSegments) {
        public Settings {
            if (fadeTicks < 1 || maxSegments < 1) {
                throw new IllegalArgumentException("fadeTicks and maxSegments must be positive");
            }
        }
    }

    public static final class Segment {
        private final Vec3d from;
        private final Vec3d to;
        private final int color;
        private int age;

        Segment(Vec3d from, Vec3d to, int color) {
            this.from = from;
            this.to = to;
            this.color = color;
        }

        public Vec3d from() {
            return from;
        }

        public Vec3d to() {
            return to;
        }

        /** 0xRRGGBB */
        public int color() {
            return color;
        }

        public int age() {
            return age;
        }
    }

    private final Deque<Segment> segments = new ArrayDeque<>();

    public void add(Vec3d from, Vec3d to, int color, Settings s) {
        segments.addLast(new Segment(from, to, color));
        while (segments.size() > s.maxSegments()) {
            segments.removeFirst();
        }
    }

    public void tick(Settings s) {
        segments.removeIf(seg -> ++seg.age >= s.fadeTicks());
    }

    public Collection<Segment> segments() {
        return Collections.unmodifiableCollection(segments);
    }

    public void clear() {
        segments.clear();
    }

    /** 経過 tick（小数可）に対する濃さ 1..0。 */
    public static float fade(float age, int fadeTicks) {
        float t = 1f - age / fadeTicks;
        return Math.max(0f, Math.min(1f, t));
    }

    /**
     * カメラからの距離に応じた倍率 0..1（{@code full} 以上で 1）。近い所を薄く・細くして、
     * 自分の弾が視界を塗りつぶさないようにする。{@code full} が 0 以下なら常に 1。
     */
    public static float ramp(double distance, double full) {
        if (full <= 0) {
            return 1f;
        }
        return (float) Math.max(0, Math.min(1, distance / full));
    }

    /**
     * 自分の弾は目の位置から出るので、銃口の方へずらして見せる。そのずらし量の倍率 1..0。
     * 銃口の近くで 1、{@code decayDistance} 離れると 0（遠くでは実際の弾道に重なる）。
     */
    public static double muzzleOffsetScale(double distance, double decayDistance) {
        if (decayDistance <= 0) {
            return 0;
        }
        return Math.max(0, 1 - distance / decayDistance);
    }

    /** 線分 a-b 上で p に一番近い点。着弾した相手の位置でビームを止めるのに使う。 */
    public static Vec3d closestOnSegment(Vec3d a, Vec3d b, Vec3d p) {
        Vec3d d = b.subtract(a);
        double len2 = d.dot(d);
        if (len2 < 1.0e-12) {
            return a;
        }
        double t = Math.max(0, Math.min(1, p.subtract(a).dot(d) / len2));
        return a.add(d.scale(t));
    }
}
