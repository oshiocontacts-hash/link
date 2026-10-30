package com.neonclash.network;

import com.neonclash.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * HUD の表示内容。
 *
 * @param phase      RoundEngine.Phase の ordinal（試合外は -1）
 * @param team       Team の ordinal（無所属は -1）
 * @param timeTicks  ラウンド中は残り時間、ラウンド間は次ラウンドまで
 */
public record S2CHudPacket(int phase, int round, int timeTicks, int redScore, int blueScore, int winsToWin,
                           int team, int redAlive, int blueAlive,
                           int blinkCharges, int blinkMax, float blinkProgress,
                           int rank, float rankProgress, float parryProgress, boolean lastStand,
                           float windup, boolean shrinking, float zoneRadius, double zoneX, double zoneZ, float jumpUpgrade) {

    public static void encode(S2CHudPacket p, FriendlyByteBuf b) {
        b.writeVarInt(p.phase + 1);
        b.writeVarInt(p.round);
        b.writeVarInt(p.timeTicks);
        b.writeVarInt(p.redScore);
        b.writeVarInt(p.blueScore);
        b.writeVarInt(p.winsToWin);
        b.writeVarInt(p.team + 1);
        b.writeVarInt(p.redAlive);
        b.writeVarInt(p.blueAlive);
        b.writeVarInt(p.blinkCharges);
        b.writeVarInt(p.blinkMax);
        b.writeFloat(p.blinkProgress);
        b.writeVarInt(p.rank);
        b.writeFloat(p.rankProgress);
        b.writeFloat(p.parryProgress);
        b.writeBoolean(p.lastStand);
        b.writeFloat(p.windup);
        b.writeBoolean(p.shrinking);
        b.writeFloat(p.zoneRadius);
        b.writeDouble(p.zoneX);
        b.writeDouble(p.zoneZ);
        b.writeFloat(p.jumpUpgrade);
    }

    public static S2CHudPacket decode(FriendlyByteBuf b) {
        return new S2CHudPacket(b.readVarInt() - 1, b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(),
                b.readVarInt(), b.readVarInt() - 1, b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(),
                b.readFloat(), b.readVarInt(), b.readFloat(), b.readFloat(), b.readBoolean(), b.readFloat(),
                b.readBoolean(), b.readFloat(), b.readDouble(), b.readDouble(), b.readFloat());
    }

    public static void handle(S2CHudPacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.onHud(p));
        ctx.get().setPacketHandled(true);
    }
}
