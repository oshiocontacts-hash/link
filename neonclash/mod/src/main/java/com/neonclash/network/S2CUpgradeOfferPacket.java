package com.neonclash.network;

import com.neonclash.client.ClientPacketHandler;
import com.neonclash.core.upgrade.UpgradeDef;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 強化の選択肢。空リストなら画面を閉じる。chosen は選択済みの番号（未選択 -1）。 */
public record S2CUpgradeOfferPacket(List<UpgradeDef> offers, int ticks, int rank, int chosen) {
    public static void encode(S2CUpgradeOfferPacket p, FriendlyByteBuf b) {
        b.writeVarInt(p.offers.size());
        for (UpgradeDef d : p.offers) {
            b.writeUtf(d.serialize());
        }
        b.writeVarInt(p.ticks);
        b.writeVarInt(p.rank);
        b.writeVarInt(p.chosen + 1);
    }

    public static S2CUpgradeOfferPacket decode(FriendlyByteBuf b) {
        int n = b.readVarInt();
        List<UpgradeDef> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(UpgradeDef.parse(b.readUtf()));
        }
        return new S2CUpgradeOfferPacket(list, b.readVarInt(), b.readVarInt(), b.readVarInt() - 1);
    }

    public static void handle(S2CUpgradeOfferPacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.onUpgradeOffer(p));
        ctx.get().setPacketHandled(true);
    }
}
