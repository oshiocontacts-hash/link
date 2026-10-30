package com.neonclash.network;

import com.neonclash.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** スロー演出（彩度低下・効果音ピッチ低下・FOV 変化）をクライアントで再生させる。 */
public record S2CSlowMoPacket(int ticks, float strength) {
    public static void encode(S2CSlowMoPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.ticks);
        buf.writeFloat(p.strength);
    }

    public static S2CSlowMoPacket decode(FriendlyByteBuf buf) {
        return new S2CSlowMoPacket(buf.readVarInt(), buf.readFloat());
    }

    public static void handle(S2CSlowMoPacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.onSlowMo(p));
        ctx.get().setPacketHandled(true);
    }
}
