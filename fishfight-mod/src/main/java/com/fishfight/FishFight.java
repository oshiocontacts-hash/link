package com.fishfight;

import com.fishfight.battle.FishBattleManager;
import com.fishfight.command.FishFightCommand;
import com.fishfight.fishing.FishingMinigameManager;
import com.fishfight.item.FishItem;
import com.fishfight.network.ModNetworking;
import com.fishfight.registry.ModComponents;
import com.fishfight.registry.ModEntities;
import com.fishfight.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FishFight implements ModInitializer {
	public static final String MOD_ID = "fishfight";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** バニラの釣り竿で魚がかかった時、ミニゲームになる確率（残りはバニラの釣果） */
	public static final float MINIGAME_CHANCE = 0.85f;

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}

	public static MutableComponent tr(String key, Object... args) {
		return Component.translatable(MOD_ID + "." + key, args);
	}

	@Override
	public void onInitialize() {
		ModComponents.init();
		ModItems.init();
		ModEntities.init();
		ModNetworking.init();

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> FishFightCommand.register(dispatcher));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			FishingMinigameManager.tick(server);
			FishBattleManager.tick(server);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			FishingMinigameManager.remove(handler.getPlayer());
			FishBattleManager.onDisconnect(handler.getPlayer());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			FishingMinigameManager.clear();
			FishBattleManager.clear();
		});

		// 魚を持って他のプレイヤーを右クリック → 勝負を挑む
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (hand != InteractionHand.MAIN_HAND || !(entity instanceof net.minecraft.world.entity.player.Player)) {
				return InteractionResult.PASS;
			}
			if (!(player.getMainHandItem().getItem() instanceof FishItem)) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide && player instanceof ServerPlayer challenger && entity instanceof ServerPlayer target) {
				FishBattleManager.challenge(challenger, target);
			}
			return InteractionResult.SUCCESS;
		});

		LOGGER.info("Fish Fight loaded. 魚に腕が生える準備ができました。");
	}
}
