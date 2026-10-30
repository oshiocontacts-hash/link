package com.neonclash;

import com.neonclash.config.NCConfig;
import com.neonclash.network.NCNetwork;
import com.neonclash.registry.NCItems;
import com.neonclash.registry.NCSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(NeonClash.MODID)
public final class NeonClash {
    public static final String MODID = "neonclash";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NeonClash() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        NCItems.ITEMS.register(modBus);
        NCItems.TABS.register(modBus);
        NCSounds.SOUNDS.register(modBus);
        modBus.addListener(this::commonSetup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, NCConfig.SERVER_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, NCConfig.CLIENT_SPEC);
        // ゲームの Forge イベントは各クラスの @Mod.EventBusSubscriber で登録している
        // （クライアント専用のものは value = Dist.CLIENT を付けて分離）
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(NCNetwork::register);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
