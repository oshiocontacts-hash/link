package com.fishfight.network;

import com.fishfight.FishFight;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** クライアント → サーバー：ミニゲームの結果 */
public record MinigameResultPayload(int session, boolean success) implements CustomPacketPayload {

	public static final Type<MinigameResultPayload> TYPE = new Type<>(FishFight.id("minigame_result"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MinigameResultPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, MinigameResultPayload::session,
			ByteBufCodecs.BOOL, MinigameResultPayload::success,
			MinigameResultPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
