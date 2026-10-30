package com.neonclash.core.round;

/** エリア縮小。縮小開始から縮小終了まで半径を線形に補間する（水平距離で判定）。 */
public record ArenaZone(double startRadius, double endRadius, int shrinkStartTick, int shrinkEndTick) {

    public double radiusAt(int elapsedTicks) {
        if (elapsedTicks <= shrinkStartTick) {
            return startRadius;
        }
        if (elapsedTicks >= shrinkEndTick || shrinkEndTick <= shrinkStartTick) {
            return endRadius;
        }
        double t = (double) (elapsedTicks - shrinkStartTick) / (shrinkEndTick - shrinkStartTick);
        return startRadius + (endRadius - startRadius) * t;
    }

    public boolean isOutside(double dx, double dz, int elapsedTicks) {
        double r = radiusAt(elapsedTicks);
        return dx * dx + dz * dz > r * r;
    }
}
