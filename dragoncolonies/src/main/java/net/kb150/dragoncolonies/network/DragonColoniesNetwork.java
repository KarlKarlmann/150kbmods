package net.kb150.dragoncolonies.network;

import net.kb150.dragoncolonies.DragonColonies;
import net.kb150.dragoncolonies.network.message.RequestRoostPointerMessage;
import net.kb150.dragoncolonies.network.message.RetrieveDragonMessage;
import net.kb150.dragoncolonies.network.message.ReleaseDragonMessage;
import net.kb150.dragoncolonies.network.message.EmergencyRecallMessage;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Netzwerk-Registry für DragonColonies.
 */
public class DragonColoniesNetwork {

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(DragonColonies.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void register() {
        // Paket 1: Anforderung des Hort-Leitstabs vom Client an den Server
        CHANNEL.registerMessage(
                packetId++,
                RequestRoostPointerMessage.class,
                RequestRoostPointerMessage::encode,
                RequestRoostPointerMessage::decode,
                RequestRoostPointerMessage::handle
        );

        // Paket 2: Drachen aus dem NBT-Speicher in die Welt holen
        CHANNEL.registerMessage(
                packetId++,
                RetrieveDragonMessage.class,
                RetrieveDragonMessage::encode,
                RetrieveDragonMessage::decode,
                RetrieveDragonMessage::handle
        );

        // Paket 3: Drachen endgültig aus dem Speicher freilassen
        CHANNEL.registerMessage(
                packetId++,
                ReleaseDragonMessage.class,
                ReleaseDragonMessage::encode,
                ReleaseDragonMessage::decode,
                ReleaseDragonMessage::handle
        );
        
        // Paket 4: Notfall-Rückruf (Setzt Deployed Status auf false)
        CHANNEL.registerMessage(
                packetId++,
                EmergencyRecallMessage.class,
                EmergencyRecallMessage::encode,
                EmergencyRecallMessage::decode,
                EmergencyRecallMessage::handle
        );
        
        DragonColonies.LOGGER.info("DragonColonies: Netzwerk-Kanal registriert. Pakete für Drachenhort sind bereit.");
    }
}