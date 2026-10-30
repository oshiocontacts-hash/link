package com.neonclash.network;

import com.neonclash.game.ActionHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** キー入力 → サーバー。判定はすべてサーバー側で行う。 */
public record C2SActionPacket(Action action, int arg) {
    public enum Action { BLINK, PARRY, SWORD, CHOOSE_UPGRADE }

    public static void encode(C2SActionPacket p, FriendlyByteBuf buf) {
        buf.writeEnum(p.action);
        buf.writeVarInt(p.arg);
    }

    public static C2SActionPacket decode(FriendlyByteBuf buf) {
        return new C2SActionPacket(buf.readEnum(Action.class), buf.readVarInt());
    }

    public static void handle(C2SActionPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        if (sender != null) {
            ActionHandler.handle(sender, p.action, p.arg);
        }
        ctx.get().setPacketHandled(true);
    }
}
