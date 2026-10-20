package net.kb150.woerp.event;

import net.kb150.woerp.Woerp;
import net.kb150.woerp.capability.PlayerRadiationProvider;
import net.kb150.woerp.network.SyncRadiationPacket;
import net.kb150.woerp.network.WoerpNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;

@Mod.EventBusSubscriber(modid = Woerp.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlayerRadiationHandler {
    private static final ResourceLocation CAP_KEY = new ResourceLocation(Woerp.MODID, "player_radiation");

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(CAP_KEY, new PlayerRadiationProvider());
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        // Bei Tod neuer Koerper = 0 Strahlung. Nur bei Portal-Teleportation bleibt die Dosis im Gewebe.
        if (!event.isWasDeath()) {
            event.getOriginal().getCapability(PlayerRadiationProvider.PLAYER_RADIATION_CAP).ifPresent(oldCap -> 
                event.getEntity().getCapability(PlayerRadiationProvider.PLAYER_RADIATION_CAP).ifPresent(newCap -> 
                    newCap.setBodyDose(oldCap.getBodyDose())
                )
            );
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        // Verhindert 1 Sekunde Render-Lag nach dem Klick auf 'Respawn', bis der Server-Tick aufwacht
        if (event.getEntity() instanceof ServerPlayer sp) {
            WoerpNetwork.sendToPlayer(new SyncRadiationPacket(0.0f, 0.0f, Collections.emptyList()), sp);
        }
    }
}