package com.neonclash.network;

import com.neonclash.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 手応え系の演出。
 * <ul>
 *   <li>SHAKE: value=揺れの強さ（度）, ticks=長さ</li>
 *   <li>HIT_STOP: ticks=移動を止める長さ（被弾側）</li>
 *   <li>HIT_CONFIRM / KILL_CONFIRM: クロスヘアのヒットマーカー。extra=連続キル数</li>
 *   <li>RANK: extra=新しいランク ordinal, value=1 なら上昇</li>
 *   <li>BEAT_HIT: ビートに乗った行動のフラッシュ</li>
 *   <li>WINDUP: 剣の溜め開始（ticks=溜め時間）。WINDUP_CANCEL: 潰された</li>
 *   <li>PARRY / PARRY_SUCCESS / LAST_STAND / BLINK</li>
 * </ul>
 */
public record S2CFeedbackPacket(Kind kind, float value, int ticks, int extra) {
    public enum Kind { SHAKE, HIT_STOP, HIT_CONFIRM, KILL_CONFIRM, RANK, BEAT_HIT, WINDUP, WINDUP_CANCEL, PARRY, PARRY_SUCCESS, LAST_STAND, BLINK }

    public static S2CFeedbackPacket of(Kind kind) {
        return new S2CFeedbackPacket(kind, 0, 0, 0);
    }

    public static void encode(S2CFeedbackPacket p, FriendlyByteBuf buf) {
        buf.writeEnum(p.kind);
        buf.writeFloat(p.value);
        buf.writeVarInt(p.ticks);
        buf.writeVarInt(p.extra);
    }

    public static S2CFeedbackPacket decode(FriendlyByteBuf buf) {
        return new S2CFeedbackPacket(buf.readEnum(Kind.class), buf.readFloat(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(S2CFeedbackPacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.onFeedback(p));
        ctx.get().setPacketHandled(true);
    }
}
