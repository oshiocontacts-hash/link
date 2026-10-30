package com.fishfight.registry;

import com.fishfight.FishFight;
import com.fishfight.entity.BattleFishEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	public static final EntityType<BattleFishEntity> BATTLE_FISH = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			FishFight.id("battle_fish"),
			EntityType.Builder.<BattleFishEntity>of(BattleFishEntity::new, MobCategory.MISC)
					.sized(0.8f, 0.6f)
					.clientTrackingRange(10)
					.updateInterval(2)
					.noSummon()
					.build("battle_fish"));

	private ModEntities() {
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(BATTLE_FISH, BattleFishEntity.createAttributes());
	}
}
