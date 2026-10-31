package com.neonclash.network;

import com.neonclash.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 弾が 1 tick に進んだ区間。クライアントは軌道のビームとして残す。ownerId は銃口へのずらしを自分の弾にだけ掛けるため。 */
public record S2CBeamPacket(double fromX, double fromY, double fromZ, double toX, double toY, double toZ, int color, int ownerId) {
    public static void encode(S2CBeamPacket p, FriendlyByteBuf buf) {
        buf.writeDouble(p.fromX);
        buf.writeDouble(p.fromY);
        buf.writeDouble(p.fromZ);
        buf.writeDouble(p.toX);
        buf.writeDouble(p.toY);
        buf.writeDouble(p.toZ);
        buf.writeInt(p.color);
        buf.writeVarInt(p.ownerId);
    }

    public static S2CBeamPacket decode(FriendlyByteBuf buf) {
        return new S2CBeamPacket(buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt(), buf.readVarInt());
    }

    public static void handle(S2CBeamPacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.onBeam(p));
        ctx.get().setPacketHandled(true);
    }
}
