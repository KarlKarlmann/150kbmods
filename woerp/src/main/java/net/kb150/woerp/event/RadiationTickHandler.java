package net.kb150.woerp.event;

import net.kb150.woerp.Woerp;
import net.kb150.woerp.capability.ChunkRadiationProvider;
import net.kb150.woerp.radiation.EmitterSavedData;
import net.kb150.woerp.radiation.RadiationEmitter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Woerp.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RadiationTickHandler {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        
        MinecraftServer server = event.getServer();
        // Nur 1x pro Sekunde ausführen, um CPU zu sparen
        if (server.getTickCount() % 20 != 0) return; 

        for (ServerLevel level : server.getAllLevels()) {
            EmitterSavedData data = EmitterSavedData.get(level);
            
            // 1. Aktive Emitter pumpen Strahlung in ihre direkten Chunks
            for (RadiationEmitter emitter : data.getEmitters()) {
                if (!emitter.isActive()) continue;
                
                ChunkPos cPos = new ChunkPos(emitter.pos);
                // Nur updaten, wenn der Chunk überhaupt im Speicher ist
                if (level.hasChunk(cPos.x, cPos.z)) {
                    LevelChunk chunk = level.getChunk(cPos.x, cPos.z);
                    chunk.getCapability(ChunkRadiationProvider.RADIATION_CAP).ifPresent(cap -> {
                        // 20 Ticks * Emission * Absorptionsrate
                        cap.setAmbient(cap.getAmbient() + (emitter.baseEmission * 20 * 0.05f));
                        chunk.setUnsaved(true);
                    });
                }
            }
            
            // 2. Spieler-Exposition errechnen & Live-Zerfall
            for (ServerPlayer player : level.players()) {
                LevelChunk playerChunk = level.getChunkAt(player.blockPosition());
                
                playerChunk.getCapability(ChunkRadiationProvider.RADIATION_CAP).ifPresent(cap -> {
                    // Live-Zerfall (nur da wo Spieler sind, der Rest macht Catch-Up beim Laden)
                    float currentAmbient = cap.getAmbient();
                    if (currentAmbient > 0.0001f) {
                        currentAmbient *= 0.99f; // 1% Zerfall pro Sekunde (kann in Config)
                        cap.setAmbient(currentAmbient);
                        playerChunk.setUnsaved(true);
                    }
                    
                    float finalDose = currentAmbient;
                    
                    // TODO: Direkte Strahlung von nahen Emittern + Inventar addieren
                    // finalDose += calculateDirectExposure(player, data);
                    // finalDose += calculateInventoryExposure(player);
                    
                    // TODO: Sync ans Client-HUD / Geigerzähler
                    // PacketHandler.sendToPlayer(new SyncRadiationPacket(finalDose), player);
                });
            }
        }
    }
}