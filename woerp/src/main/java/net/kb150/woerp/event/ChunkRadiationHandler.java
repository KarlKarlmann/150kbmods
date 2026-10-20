package net.kb150.woerp.event;

import net.kb150.woerp.Woerp;
import net.kb150.woerp.capability.ChunkRadiationProvider;
import net.kb150.woerp.radiation.EmitterSavedData;
import net.kb150.woerp.radiation.RadiationEmitter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Woerp.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChunkRadiationHandler {

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide() || !(event.getChunk() instanceof LevelChunk chunk)) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        chunk.getCapability(ChunkRadiationProvider.RADIATION_CAP).ifPresent(cap -> {
            long currentTick = serverLevel.getServer().getTickCount();
            long lastUpdate = cap.getLastUpdateTick();
            long deltaTicks = currentTick - lastUpdate;

            if (deltaTicks <= 0) return;

            double deltaSeconds = deltaTicks / 20.0;
            EmitterSavedData data = EmitterSavedData.get(serverLevel);
            BlockPos midBlock = chunk.getPos().getMiddleBlockPosition(serverLevel.getSeaLevel());
            float maxTarget = 0.0f;

            for (RadiationEmitter emitter : data.getEmitters()) {
                if (!emitter.isActive()) continue;
                double distSq = emitter.pos.distSqr(midBlock);
                if (distSq <= (RadiationEmitter.GLOBAL_RADIUS * RadiationEmitter.GLOBAL_RADIUS)) {
                    float field = (float) (emitter.getBaseEmission() * (1.0 - (Math.sqrt(distSq) / RadiationEmitter.GLOBAL_RADIUS)));
                    if (field > maxTarget) maxTarget = field;
                }
            }
            maxTarget = Math.min(500.0f, maxTarget);

            // Analytische Integration: Berechnet das Gleichgewicht ueber Offline-Perioden in O(1)
            double decayCoeff = Math.exp(-0.01511 * deltaSeconds);
            float current = cap.getAmbient();
            float updated = (float) (maxTarget + (current - maxTarget) * decayCoeff);

            cap.setAmbient(updated);
            cap.setLastUpdateTick(currentTick);
            chunk.setUnsaved(true);

            // Reaktiviert Chunks mit Reststrahlung im RAM-Simulations-Set
            if (updated > 0.001f || maxTarget > 0.001f) {
                RadiationTickHandler.trackChunk(serverLevel.dimension(), chunk.getPos());
            }
        });
    }
}