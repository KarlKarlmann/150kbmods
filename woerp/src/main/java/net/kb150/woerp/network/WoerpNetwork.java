package net.kb150.woerp.network;

import net.kb150.woerp.Woerp;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class WoerpNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Woerp.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals
    );
    private static int id = 0;

    public static void register() {
        CHANNEL.messageBuilder(SyncRadiationPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncRadiationPacket::encode)
                .decoder(SyncRadiationPacket::new)
                .consumerNetworkThread(SyncRadiationPacket::handle)
                .add();
    }

    public static void sendToPlayer(Object packet, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}