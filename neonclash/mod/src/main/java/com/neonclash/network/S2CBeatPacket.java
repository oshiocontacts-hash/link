package com.neonclash.network;

import com.neonclash.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** サーバーのビート位相。serverNowMs でクライアントとの時計のずれを補正する。 */
public record S2CBeatPacket(double bpm, double anchorMs, long anchorIndex, double toleranceMs, boolean enabled, long serverNowMs) {
    public static void encode(S2CBeatPacket p, FriendlyByteBuf b) {
        b.writeDouble(p.bpm);
        b.writeDouble(p.anchorMs);
        b.writeLong(p.anchorIndex);
        b.writeDouble(p.toleranceMs);
        b.writeBoolean(p.enabled);
        b.writeLong(p.serverNowMs);
    }

    public static S2CBeatPacket decode(FriendlyByteBuf b) {
        return new S2CBeatPacket(b.readDouble(), b.readDouble(), b.readLong(), b.readDouble(), b.readBoolean(), b.readLong());
    }

    public static void handle(S2CBeatPacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.onBeat(p));
        ctx.get().setPacketHandled(true);
    }
}
