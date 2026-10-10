package net.kb150.woerp.event;

import net.kb150.woerp.Woerp;
import net.kb150.woerp.capability.ChunkRadiationProvider;
import net.kb150.woerp.config.WoerpConfig;
import net.kb150.woerp.radiation.EmitterSavedData;
import net.kb150.woerp.radiation.RadiationEmitter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Woerp.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChunkRadiationHandler {

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        // Logik läuft exklusiv serverseitig auf echten LevelChunks
        if (event.getLevel().isClientSide() || !(event.getChunk() instanceof LevelChunk chunk)) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        chunk.getCapability(ChunkRadiationProvider.RADIATION_CAP).ifPresent(cap -> {
            long currentTick = serverLevel.getServer().getTickCount();
            long lastUpdate = cap.getLastUpdateTick();
            long deltaTicks = currentTick - lastUpdate;

            // Chunk ist bereits up-to-date (z.B. frisch generiert in diesem Tick)
            if (deltaTicks <= 0) return;

            float halfLife = WoerpConfig.HALF_LIFE_TICKS.get().floatValue();
            
            // 1. Exponentieller Zerfall der alten Umgebungsstrahlung
            double decayFactor = Math.pow(0.5, deltaTicks / (double) halfLife);
            float currentAmbient = (float) (cap.getAmbient() * decayFactor);

            // 2. Verpasste Strahlung aller Emitter im Radius nachholen
            EmitterSavedData data = EmitterSavedData.get(serverLevel);
            float accumulated = 0.0f;

            for (RadiationEmitter emitter : data.getEmitters()) {
                // Wir betrachten nur das Zeitfenster, in dem sowohl Chunk offline als auch Emitter aktiv war
                long overlapStart = Math.max(lastUpdate, emitter.startTick);
                long activeEnd = (emitter.endTick == -1L) ? currentTick : emitter.endTick;
                long overlapEnd = Math.min(currentTick, activeEnd);
                
                long activeTicks = overlapEnd - overlapStart;
                
                if (activeTicks > 0) {
                    double distSq = emitter.pos.distSqr(chunk.getPos().getMiddleBlockPosition(64));
                    
                    if (distSq <= (emitter.maxRadius * emitter.maxRadius)) {
                        // Inverse-Square-Law für Entfernungsabnahme
                        float factor = (float) (emitter.baseEmission / Math.max(1.0, distSq));
                        // 0.05f = Absorptionsrate der Umgebung (kann später in Config)
                        accumulated += factor * activeTicks * 0.05f; 
                    }
                }
            }

            // 3. Zustand einbrennen
            cap.setAmbient(currentAmbient + accumulated);
            cap.setLastUpdateTick(currentTick);
            chunk.setUnsaved(true); // Zwingt den Chunk zum Speichern der neuen NBT-Daten
        });
    }
}