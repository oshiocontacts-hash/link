package com.fishfight.network;

import com.fishfight.FishFight;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** サーバー → クライアント：釣りミニゲームを開始する */
public record StartMinigamePayload(int session, String species, int rarity, float difficulty, boolean armsGag)
		implements CustomPacketPayload {

	public static final Type<StartMinigamePayload> TYPE = new Type<>(FishFight.id("start_minigame"));
	public static final StreamCodec<RegistryFriendlyByteBuf, StartMinigamePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, StartMinigamePayload::session,
			ByteBufCodecs.STRING_UTF8, StartMinigamePayload::species,
			ByteBufCodecs.VAR_INT, StartMinigamePayload::rarity,
			ByteBufCodecs.FLOAT, StartMinigamePayload::difficulty,
			ByteBufCodecs.BOOL, StartMinigamePayload::armsGag,
			StartMinigamePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
