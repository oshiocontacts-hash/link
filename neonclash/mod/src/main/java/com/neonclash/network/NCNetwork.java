package com.neonclash.network;

import com.neonclash.NeonClash;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class NCNetwork {
    private static final String PROTOCOL = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            NeonClash.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private NCNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(C2SActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SActionPacket::encode).decoder(C2SActionPacket::decode)
                .consumerMainThread(C2SActionPacket::handle).add();
        CHANNEL.messageBuilder(S2CFeedbackPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CFeedbackPacket::encode).decoder(S2CFeedbackPacket::decode)
                .consumerMainThread(S2CFeedbackPacket::handle).add();
        CHANNEL.messageBuilder(S2CSlowMoPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CSlowMoPacket::encode).decoder(S2CSlowMoPacket::decode)
                .consumerMainThread(S2CSlowMoPacket::handle).add();
        CHANNEL.messageBuilder(S2CHudPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CHudPacket::encode).decoder(S2CHudPacket::decode)
                .consumerMainThread(S2CHudPacket::handle).add();
        CHANNEL.messageBuilder(S2CBeatPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CBeatPacket::encode).decoder(S2CBeatPacket::decode)
                .consumerMainThread(S2CBeatPacket::handle).add();
        CHANNEL.messageBuilder(S2CUpgradeOfferPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CUpgradeOfferPacket::encode).decoder(S2CUpgradeOfferPacket::decode)
                .consumerMainThread(S2CUpgradeOfferPacket::handle).add();
        CHANNEL.messageBuilder(S2CBeamPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CBeamPacket::encode).decoder(S2CBeamPacket::decode)
                .consumerMainThread(S2CBeamPacket::handle).add();
    }

    /** 実プレイヤーにだけ送る（モブ・FakePlayer は無視）。 */
    public static void send(Entity target, Object msg) {
        if (target instanceof ServerPlayer sp && !(sp instanceof FakePlayer) && sp.connection != null) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), msg);
        }
    }

    public static void sendToLevel(ServerLevel level, Object msg) {
        CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), msg);
    }

    /** pos から radius 以内のプレイヤーに送る。 */
    public static void sendNear(ServerLevel level, Vec3 pos, double radius, Object msg) {
        CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(pos.x, pos.y, pos.z, radius, level.dimension())), msg);
    }

    public static void sendToAll(Object msg) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), msg);
    }

    public static void sendToServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }
}
