package net.kb150.survivorcolonies.network;

import net.kb150.survivorcolonies.SurvivorColonies;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModMessages {

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SurvivorColonies.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;
    private static int id() {
        return packetId++;
    }

    public static void register() {
        // 1. Rekrutierungs-Paket
        INSTANCE.messageBuilder(C2SRecruitSurvivorPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SRecruitSurvivorPacket::new)
                .encoder(C2SRecruitSurvivorPacket::encode)
                .consumerMainThread(C2SRecruitSurvivorPacket::handle)
                .add();

        // 2. NEU: Handels-Paket
        INSTANCE.messageBuilder(C2STradeOfferPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2STradeOfferPacket::new)
                .encoder(C2STradeOfferPacket::toBytes)
                .consumerMainThread(C2STradeOfferPacket::handle)
                .add();
    }

    public static <MSG> void sendToServer(MSG message) {
        INSTANCE.sendToServer(message);
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}