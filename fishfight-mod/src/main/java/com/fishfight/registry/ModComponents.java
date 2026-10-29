package com.fishfight.registry;

import com.fishfight.FishFight;
import com.fishfight.fish.FishStats;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModComponents {
	public static final DataComponentType<FishStats> FISH_STATS = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			FishFight.id("fish_stats"),
			DataComponentType.<FishStats>builder()
					.persistent(FishStats.CODEC)
					.networkSynchronized(FishStats.STREAM_CODEC)
					.build());

	private ModComponents() {
	}

	public static void init() {
	}
}
