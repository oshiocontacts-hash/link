package com.fishfight.registry;

import com.fishfight.FishFight;
import com.fishfight.fish.FishSpecies;
import com.fishfight.item.FishItem;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ModItems {
	public static final Map<FishSpecies, FishItem> FISH = new EnumMap<>(FishSpecies.class);

	static {
		for (FishSpecies species : FishSpecies.values()) {
			FishItem item = new FishItem(species, new Item.Properties().stacksTo(1));
			FISH.put(species, Registry.register(BuiltInRegistries.ITEM, FishFight.id(species.id), item));
		}
	}

	public static final CreativeModeTab TAB = Registry.register(
			BuiltInRegistries.CREATIVE_MODE_TAB,
			FishFight.id("main"),
			FabricItemGroup.builder()
					.icon(() -> new ItemStack(FISH.get(FishSpecies.TUNA)))
					.title(Component.translatable("itemGroup.fishfight"))
					.displayItems((params, output) -> FISH.values().forEach(output::accept))
					.build());

	private ModItems() {
	}

	public static FishItem fish(FishSpecies species) {
		return FISH.get(species);
	}

	public static void init() {
	}
}
