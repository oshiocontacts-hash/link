package com.neonclash.match;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** /pvp arena set で決めたアリーナ中心（次元ごとに保存）。 */
public final class ArenaData extends SavedData {
    private static final String NAME = "neonclash_arena";
    @Nullable
    private Vec3 center;

    public static ArenaData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ArenaData::load, ArenaData::new, NAME);
    }

    private static ArenaData load(CompoundTag tag) {
        ArenaData d = new ArenaData();
        if (tag.contains("x")) {
            d.center = new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (center != null) {
            tag.putDouble("x", center.x);
            tag.putDouble("y", center.y);
            tag.putDouble("z", center.z);
        }
        return tag;
    }

    @Nullable
    public Vec3 center() {
        return center;
    }

    public void setCenter(Vec3 c) {
        center = c;
        setDirty();
    }
}
