package com.fishfight.client;

import com.fishfight.network.StartMinigamePayload;
import com.fishfight.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class FishFightClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityModelLayerRegistry.registerModelLayer(BattleFishModel.LAYER, BattleFishModel::createBodyLayer);
		EntityRendererRegistry.register(ModEntities.BATTLE_FISH, BattleFishRenderer::new);

		ClientPlayNetworking.registerGlobalReceiver(StartMinigamePayload.TYPE, (payload, context) ->
				context.client().setScreen(new FishingMinigameScreen(payload)));
	}
}
