package net.kb150.woerp.event;

import net.kb150.woerp.Woerp;
import net.kb150.woerp.capability.ChunkRadiationProvider;
import net.kb150.woerp.capability.PlayerRadiationProvider;
import net.kb150.woerp.network.SyncRadiationPacket;
import net.kb150.woerp.network.WoerpNetwork;
import net.kb150.woerp.radiation.EmitterSavedData;
import net.kb150.woerp.radiation.ItemRadiationRegistry;
import net.kb150.woerp.radiation.RadiationEmitter;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = Woerp.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RadiationTickHandler {
    private static final Set<UUID> TRACKED_PLAYERS = new HashSet<>();
    private static final double GLOBAL_RADIUS_SQ = RadiationEmitter.GLOBAL_RADIUS * RadiationEmitter.GLOBAL_RADIUS;
    private static final int CHUNK_RADIUS = (int) Math.ceil(RadiationEmitter.GLOBAL_RADIUS / 16.0);
    
    // Verhindert O(N)-Full-World-Scans: Speichert nur Chunks im RAM, die aktiv strahlen oder im Wirkfeld liegen
    private static final Map<ResourceKey<Level>, Set<ChunkPos>> CONTAMINATED_CHUNKS = new ConcurrentHashMap<>();

    public static void trackChunk(ResourceKey<Level> dim, ChunkPos pos) {
        CONTAMINATED_CHUNKS.computeIfAbsent(dim, k -> ConcurrentHashMap.newKeySet()).add(pos);
    }

    @SubscribeEvent
    public static void onTagsUpdated(net.minecraftforge.event.TagsUpdatedEvent event) {
        ItemRadiationRegistry.rebuildCache();
        Woerp.LOGGER.info("[WOERP] Item-Strahlungs-Cache neu aufgebaut.");
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;

        for (ServerLevel level : server.getAllLevels()) {
            EmitterSavedData data = EmitterSavedData.get(level);
            ResourceKey<Level> dim = level.dimension();
            Set<ChunkPos> activeChunks = CONTAMINATED_CHUNKS.computeIfAbsent(dim, k -> ConcurrentHashMap.newKeySet());

            // Registriert alle geladenen Chunks im Wirkungsradius aktiver Emitter
            for (RadiationEmitter emitter : data.getEmitters()) {
                if (!emitter.isActive()) continue;
                ChunkPos center = new ChunkPos(emitter.pos);
                for (int cx = center.x - CHUNK_RADIUS; cx <= center.x + CHUNK_RADIUS; cx++) {
                    for (int cz = center.z - CHUNK_RADIUS; cz <= center.z + CHUNK_RADIUS; cz++) {
                        if (level.getChunkSource().getChunkNow(cx, cz) != null) {
                            activeChunks.add(new ChunkPos(cx, cz));
                        }
                    }
                }
            }

            int processedChunks = 0;
            Iterator<ChunkPos> it = activeChunks.iterator();
            while (it.hasNext()) {
                ChunkPos cPos = it.next();
                LevelChunk chunk = level.getChunkSource().getChunkNow(cPos.x, cPos.z);
                
                // Chunk wurde entladen: Berechnung pausiert im RAM, onChunkLoad rechnet die Offline-Zeit nach
                if (chunk == null) {
                    it.remove();
                    continue;
                }

                BlockPos midBlock = cPos.getMiddleBlockPosition(level.getSeaLevel());
                float maxTarget = 0.0f;
                float totalFlow = 0.0f;

                for (RadiationEmitter e : data.getEmitters()) {
                    if (!e.isActive()) continue;
                    double distSq = e.pos.distSqr(midBlock);
                    if (distSq <= GLOBAL_RADIUS_SQ) {
                        float field = (float) (e.getBaseEmission() * (1.0 - (Math.sqrt(distSq) / RadiationEmitter.GLOBAL_RADIUS)));
                        if (field > maxTarget) maxTarget = field;
                        totalFlow += field * 0.0002f;
                    }
                }

                maxTarget = Math.min(500.0f, maxTarget);
                final float target = maxTarget;
                final float rate = Math.min(0.15f, 0.015f + totalFlow);

                chunk.getCapability(ChunkRadiationProvider.RADIATION_CAP).ifPresent(cap -> {
                    float current = cap.getAmbient();
                    float next = current + (target - current) * rate;

                    // Dekontaminierte Chunks aus dem Ticking-Set entlassen
                    if (next <= 0.001f && target <= 0.001f) {
                        cap.setAmbient(0.0f);
                        it.remove();
                    } else {
                        cap.setAmbient(next);
                    }
                    chunk.setUnsaved(true);
                });
                processedChunks++;
            }

            if (server.getTickCount() % 100 == 0 && processedChunks > 0) {
                Woerp.LOGGER.info("[WOERP-TICK-DEBUG] Level {}: {} geladene Chunks simuliert (Aktiv im Set: {})", 
                        dim.location(), processedChunks, activeChunks.size());
            }

            // Client-Sync: Sendet Strahlungs-Snapshot und sichtbare Emitter an anwesende Spieler
			for (ServerPlayer player : level.players()) {
                BlockPos pPos = player.blockPosition();
                LevelChunk pChunk = level.getChunkSource().getChunkNow(pPos.getX() >> 4, pPos.getZ() >> 4);
                float ambientDose = (pChunk != null) ? pChunk.getCapability(ChunkRadiationProvider.RADIATION_CAP).map(c -> c.getAmbient()).orElse(0.0f) : 0.0f;
                // Verhindert das Ueberschreiben manuell gesetzter oder akkumulierter Koerperdosen durch den 1-Sekunden-Tick
                float bodyDose = player.getCapability(PlayerRadiationProvider.PLAYER_RADIATION_CAP).map(c -> c.getBodyDose()).orElse(0.0f);

                List<BlockPos> nearbyEmitters = new ArrayList<>();
                for (RadiationEmitter e : data.getEmitters()) {
                    if (e.isActive() && e.pos.distSqr(pPos) <= (48.0 * 48.0)) nearbyEmitters.add(e.pos);
                }
                nearbyEmitters.sort(Comparator.comparingDouble(pos -> pos.distSqr(pPos)));

                UUID uuid = player.getUUID();
                if (ambientDose > 0.01f || bodyDose > 0.01f || !nearbyEmitters.isEmpty()) {
                    TRACKED_PLAYERS.add(uuid);
                    WoerpNetwork.sendToPlayer(new SyncRadiationPacket(ambientDose, bodyDose, nearbyEmitters), player);
                } else if (TRACKED_PLAYERS.remove(uuid)) {
                    WoerpNetwork.sendToPlayer(new SyncRadiationPacket(0.0f, 0.0f, Collections.emptyList()), player);
                }
            }
        }
    }
}