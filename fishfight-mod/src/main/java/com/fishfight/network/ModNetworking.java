package com.fishfight.network;

import com.fishfight.fishing.FishingMinigameManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class ModNetworking {
	private ModNetworking() {
	}

	public static void init() {
		PayloadTypeRegistry.playS2C().register(StartMinigamePayload.TYPE, StartMinigamePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(MinigameResultPayload.TYPE, MinigameResultPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(MinigameResultPayload.TYPE, (payload, context) ->
				FishingMinigameManager.handleResult(context.player(), payload.session(), payload.success()));
	}
}
