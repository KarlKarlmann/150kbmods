package net.kb150.meteorshower;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MeteorShower.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void register(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            INSTANCE.messageBuilder(MeteorSpawnPacket.class, packetId++)
                    .encoder(MeteorSpawnPacket::toBytes)
                    .decoder(MeteorSpawnPacket::new)
                    .consumerMainThread(MeteorSpawnPacket::handle)
                    .add();
        });
    }
}