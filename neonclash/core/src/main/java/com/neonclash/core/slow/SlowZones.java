package com.neonclash.core.slow;

import com.neonclash.core.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** 複数の範囲スロー。重なった場合は一番強い（倍率が小さい）ものを採用する。 */
public final class SlowZones {
    private final List<SlowZone> zones = new ArrayList<>();

    public void add(SlowZone zone) {
        zones.add(zone);
    }

    public void prune(long nowTick) {
        zones.removeIf(z -> z.expired(nowTick));
    }

    /** 位置 p の速度倍率（どのゾーンにも入っていなければ 1.0）。 */
    public double factorAt(Vec3d p, long nowTick) {
        double f = 1.0;
        for (SlowZone z : zones) {
            if (!z.expired(nowTick) && z.contains(p)) {
                f = Math.min(f, z.factor());
            }
        }
        return f;
    }

    public List<SlowZone> active() {
        return List.copyOf(zones);
    }

    public void clear() {
        zones.clear();
    }
}
