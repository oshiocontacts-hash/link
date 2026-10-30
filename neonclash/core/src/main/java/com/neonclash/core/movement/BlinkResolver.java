package com.neonclash.core.movement;

import com.neonclash.core.math.Box;
import com.neonclash.core.math.CollisionQuery;
import com.neonclash.core.math.Vec3d;

import java.util.Optional;

/**
 * ブリンク先の計算。視線方向へ少しずつ箱を進め、最初にぶつかった所で止める（壁の向こうへは抜けない）。
 * 床・天井に当たったときは小さく上下に補正して、めり込まない位置を探す。
 */
public final class BlinkResolver {

    /**
     * @param distance       最大ワープ距離（ブロック）
     * @param step           サンプリング間隔。エンティティ幅・高さより小さくすること（すり抜け防止）
     * @param maxStepUp      床にぶつかった時に持ち上げてよい最大量
     * @param maxCeilingDrop 天井にぶつかった時に下げてよい最大量
     * @param minDistance    これ未満しか進めないならブリンク失敗（チャージを消費しない）
     */
    public record Settings(double distance, double step, double maxStepUp, double maxCeilingDrop, double minDistance) {
        public Settings {
            if (step <= 0) {
                throw new IllegalArgumentException("step must be > 0");
            }
        }
    }

    public record Result(Vec3d destination, double travelled) {
    }

    private BlinkResolver() {
    }

    public static Optional<Result> resolve(Vec3d feet, Vec3d look, double width, double height,
                                           Settings s, CollisionQuery world) {
        Vec3d dir = look.normalize();
        if (dir.equals(Vec3d.ZERO) || s.distance() <= 0) {
            return Optional.empty();
        }
        // サンプル間隔が箱より大きいと薄い壁を飛び越えてしまうので、必ず箱より細かく刻む
        double step = Math.min(s.step(), Math.min(width, height) * 0.5);
        Vec3d last = feet;
        double left = s.distance();
        while (left > 1.0e-9) {
            double d = Math.min(step, left);
            Optional<Vec3d> fixed = fit(last, last.add(dir.scale(d)), width, height, s, step, world);
            if (fixed.isEmpty()) {
                break;
            }
            last = fixed.get();
            left -= d;
        }
        double moved = last.distanceTo(feet);
        if (moved < s.minDistance()) {
            return Optional.empty();
        }
        return Optional.of(new Result(last, moved));
    }

    /** candidate がそのまま空いていればそれを、駄目なら上下に少しずらして空いている位置を返す。 */
    private static Optional<Vec3d> fit(Vec3d from, Vec3d candidate, double width, double height,
                                       Settings s, double step, CollisionQuery world) {
        if (world.isFree(Box.ofEntity(candidate, width, height))) {
            return Optional.of(candidate);
        }
        // 床：持ち上げる。持ち上げる経路（from の真上）が途中で塞がったら打ち切る＝天井を抜けない
        Optional<Vec3d> lifted = shift(from, candidate, step, s.maxStepUp(), width, height, world);
        if (lifted.isPresent()) {
            return lifted;
        }
        // 天井：下げる
        return shift(from, candidate, -step, s.maxCeilingDrop(), width, height, world);
    }

    private static Optional<Vec3d> shift(Vec3d from, Vec3d candidate, double delta, double limit,
                                         double width, double height, CollisionQuery world) {
        for (double moved = Math.abs(delta); moved <= limit + 1.0e-9; moved += Math.abs(delta)) {
            double dy = Math.signum(delta) * moved;
            if (!world.isFree(Box.ofEntity(from.add(0, dy, 0), width, height))) {
                return Optional.empty();
            }
            Vec3d shifted = candidate.add(0, dy, 0);
            if (world.isFree(Box.ofEntity(shifted, width, height))) {
                return Optional.of(shifted);
            }
        }
        return Optional.empty();
    }
}
