package com.neonclash.registry;

import com.neonclash.NeonClash;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** data/neonclash/damage_type/*.json で定義（防具・エンチャント・無敵時間を無視するタグ付き）。 */
public final class NCDamageTypes {
    public static final ResourceKey<DamageType> BLADE = ResourceKey.create(Registries.DAMAGE_TYPE, NeonClash.id("blade"));
    public static final ResourceKey<DamageType> ZONE = ResourceKey.create(Registries.DAMAGE_TYPE, NeonClash.id("zone"));

    private NCDamageTypes() {
    }

    public static DamageSource source(Level level, ResourceKey<DamageType> key, Entity attacker) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key), attacker);
    }

    public static DamageSource source(Level level, ResourceKey<DamageType> key) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key));
    }
}
