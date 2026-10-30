package com.neonclash.registry;

import com.neonclash.NeonClash;
import com.neonclash.game.NeonBladeItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class NCItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, NeonClash.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NeonClash.MODID);

    public static final RegistryObject<Item> NEON_BLADE = ITEMS.register("neon_blade",
            () -> new NeonBladeItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.neonclash"))
            .icon(() -> new ItemStack(NEON_BLADE.get()))
            .displayItems((params, out) -> out.accept(NEON_BLADE.get()))
            .build());

    private NCItems() {
    }
}
