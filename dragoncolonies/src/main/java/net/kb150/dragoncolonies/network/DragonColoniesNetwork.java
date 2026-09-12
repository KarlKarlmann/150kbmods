package net.kb150.dragoncolonies.network;

import net.kb150.dragoncolonies.DragonColonies;
import net.kb150.dragoncolonies.network.message.RequestRoostPointerMessage;
import net.kb150.dragoncolonies.network.message.RetrieveDragonMessage;
import net.kb150.dragoncolonies.network.message.ReleaseDragonMessage;
import net.kb150.dragoncolonies.network.message.EmergencyRecallMessage;
import net.kb150.dragoncolonies.network.message.RequestExportOffersMessage;
import net.kb150.dragoncolonies.network.message.OpenExportWindowMessage;
import net.kb150.dragoncolonies.network.message.AcceptExportOfferMessage;
import net.kb150.dragoncolonies.network.message.ToggleBreedingStatusMessage; // <-- NEU
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
        CHANNEL.registerMessage(
                packetId++,
                RequestRoostPointerMessage.class,
                RequestRoostPointerMessage::encode,
                RequestRoostPointerMessage::decode,
                RequestRoostPointerMessage::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                RetrieveDragonMessage.class,
                RetrieveDragonMessage::encode,
                RetrieveDragonMessage::decode,
                RetrieveDragonMessage::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                ReleaseDragonMessage.class,
                ReleaseDragonMessage::encode,
                ReleaseDragonMessage::decode,
                ReleaseDragonMessage::handle
        );
        
        CHANNEL.registerMessage(
                packetId++,
                EmergencyRecallMessage.class,
                EmergencyRecallMessage::encode,
                EmergencyRecallMessage::decode,
                EmergencyRecallMessage::handle
        );
        
        CHANNEL.registerMessage(
                packetId++,
                RequestExportOffersMessage.class,
                RequestExportOffersMessage::encode,
                RequestExportOffersMessage::decode,
                RequestExportOffersMessage::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                OpenExportWindowMessage.class,
                OpenExportWindowMessage::encode,
                OpenExportWindowMessage::decode,
                OpenExportWindowMessage::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                AcceptExportOfferMessage.class,
                AcceptExportOfferMessage::encode,
                AcceptExportOfferMessage::decode,
                AcceptExportOfferMessage::handle
        );

        // Paket 8: Zucht Status Umschalter
        CHANNEL.registerMessage(
                packetId++,
                ToggleBreedingStatusMessage.class,
                ToggleBreedingStatusMessage::encode,
                ToggleBreedingStatusMessage::decode,
                ToggleBreedingStatusMessage::handle
        );

        DragonColonies.LOGGER.info("DragonColonies: Netzwerk-Kanal registriert. Pakete für Drachenhort sind bereit.");
    }
}