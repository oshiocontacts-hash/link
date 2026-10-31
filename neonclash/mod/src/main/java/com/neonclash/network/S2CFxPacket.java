package com.neonclash.network;

import com.neonclash.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 世界の中に出す演出。見た目だけで判定には関わらない。
 * <ul>
 *   <li>MUZZLE: dir=銃の向き, extra=撃った人のエンティティ ID, value=1 ならビートに乗った射撃</li>
 *   <li>HIT: dir=弾の向き, extra={@link #BEAT} {@link #HEAD} の組み合わせ</li>
 *   <li>IMPACT: dir=壁の法線</li>
 *   <li>KILL: dir=倒した弾や斬撃の向き, extra=倒された側の色</li>
 *   <li>SLASH: pos=目の位置, dir=視線, value=射程</li>
 *   <li>BLINK: pos=出発点, dir=到着点までのベクトル, value=体の高さ</li>
 *   <li>PARRY: value=範囲スローの半径</li>
 *   <li>LAST_STAND: value=体の高さ</li>
 *   <li>WINDUP: extra=エンティティ ID, value=溜めの tick 数</li>
 *   <li>DAMAGE: value=ダメージ, extra={@link #BEAT} {@link #HEAD}（当てた本人にだけ送る）</li>
 * </ul>
 */
public record S2CFxPacket(Kind kind, double x, double y, double z, float dx, float dy, float dz, int color, float value, int extra) {
    public enum Kind { MUZZLE, HIT, IMPACT, KILL, SLASH, BLINK, PARRY, LAST_STAND, WINDUP, DAMAGE }

    public static final int BEAT = 1;
    public static final int HEAD = 2;

    public static void encode(S2CFxPacket p, FriendlyByteBuf buf) {
        buf.writeEnum(p.kind);
        buf.writeDouble(p.x);
        buf.writeDouble(p.y);
        buf.writeDouble(p.z);
        buf.writeFloat(p.dx);
        buf.writeFloat(p.dy);
        buf.writeFloat(p.dz);
        buf.writeInt(p.color);
        buf.writeFloat(p.value);
        buf.writeVarInt(p.extra);
    }

    public static S2CFxPacket decode(FriendlyByteBuf buf) {
        return new S2CFxPacket(buf.readEnum(Kind.class), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readInt(), buf.readFloat(), buf.readVarInt());
    }

    public static void handle(S2CFxPacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.onFx(p));
        ctx.get().setPacketHandled(true);
    }
}
