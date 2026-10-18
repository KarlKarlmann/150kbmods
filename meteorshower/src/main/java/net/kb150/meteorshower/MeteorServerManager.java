package net.kb150.meteorshower;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.PacketDistributor;

import java.io.RandomAccessFile;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MeteorServerManager {

    private static final int DELAY_BETWEEN_SPAWNS = 40;
    private static final int FLIGHT_DURATION = 80;
    // 38 ms entspricht ca. 75 % des 50-ms-Budgets; lässt 12 ms Puffer für Entity-Ticks und Networking.
    private static final double HEADROOM_THRESHOLD_MS = 38.0; 
    private static final int COOLDOWN_TICKS = 100; 
    private static final double EMERGENCY_DISTANCE_SQ = 400.0 * 400.0;

    private static int spawnTimer = Integer.MIN_VALUE;
    private static boolean eventStarted = false;
    private static final LongArrayList VALID_CHUNKS = new LongArrayList();
    private static boolean mapScanned = false;

    private static ImpactJob currentJob = null; 
    private static long nextEligibleTick = 0;
    private static boolean pausedDhForThisJob = false; 

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide()) return;
        if (!(event.level instanceof ServerLevel serverLevel) || serverLevel.dimension() != Level.OVERWORLD) return;

        MeteorEventData data = getOrCreateData(serverLevel);
        long tick = serverLevel.getServer().getTickCount();

        handleEventSpawning(serverLevel, data);

        // Ein aktiver Pipeline-Job blockiert alle Folgestarts, um die Tick-Belastung strikt auf eine Baustelle zu begrenzen.
        if (currentJob != null) {
            advanceJob(serverLevel, data);
            return;
        }

        if (data.pendingImpacts.isEmpty()) return;

        if (handleEmergencyTriggers(serverLevel, data, tick)) return;

        handleBackgroundScheduler(serverLevel, data, tick);
    }

    private static void handleEventSpawning(ServerLevel level, MeteorEventData data) {
        if (data.isFinished) return;

        if (spawnTimer == Integer.MIN_VALUE) spawnTimer = MeteorConfig.eventStartDelay;

        if (!eventStarted) {
            if (!level.players().isEmpty()) {
                if (MeteorConfig.eventStartDelay < 0) return;

                if (--spawnTimer <= 0) {
                    eventStarted = true;
                    MeteorShower.LOGGER.info("[MeteorServerManager] Countdown has expired. Event is active.");
                }
            }
            return;
        }

        if (data.meteorsSpawnedSoFar < data.totalMeteorsToSpawn) {
            if (--spawnTimer <= 0) {
                if (triggerMeteor(level, data)) {
                    data.meteorsSpawnedSoFar++;
                    data.setDirty();
                    spawnTimer = DELAY_BETWEEN_SPAWNS;
                } else {
                    spawnTimer = 5; 
                }
            }
        } else if (currentJob == null && data.pendingImpacts.isEmpty()) {
            data.isFinished = true;
            data.setDirty();
            eventStarted = false;
            spawnTimer = MeteorConfig.eventStartDelay;
            MeteorShower.LOGGER.info("[MeteorServerManager] All scheduled meteors have struck. The shower has ended.");
        }
    }

    private static boolean handleEmergencyTriggers(ServerLevel level, MeteorEventData data, long tick) {
        for (PendingImpact p : data.pendingImpacts) {
            if (tick < p.readyTick) continue; 

            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(p.x, player.getY(), p.z) <= EMERGENCY_DISTANCE_SQ) {
                    MeteorShower.LOGGER.info("[MeteorServerManager] Spieler in 400m-Reichweite von Krater [{}, {}]. Notfall-Job initiiert.", p.x, p.z);
                    startJob(level, p, true); 
                    return true;
                }
            }
        }
        return false;
    }

    private static void handleBackgroundScheduler(ServerLevel level, MeteorEventData data, long tick) {
        if (tick < nextEligibleTick || !serverHasHeadroom(level.getServer())) return; 

        PendingImpact best = pickNext(level, data, tick);
        if (best != null) {
            MeteorShower.LOGGER.info("[MeteorServerManager] Freie Serverkapazität erkannt. Starte Hintergrund-Job [{}, {}].", best.x, best.z);
            startJob(level, best, false); 
        }
    }

    private static void advanceJob(ServerLevel level, MeteorEventData data) {
        ImpactJob job = currentJob;

        if (--job.timeout <= 0) {
            MeteorShower.LOGGER.warn("[MeteorServerManager] Chunk load timeout for craters [{}, {}]. Aborting.", job.source.x, job.source.z);
            terminateJob(level, data, job);
            return;
        }

        // Phase 1: Warten auf vollständige Generierung/Bereitstellung aller erzwungenen Chunks im RAM.
        if (job.pipeline == null) {
            int missing = 0;
            for (ChunkPos cp : job.forcedChunks) {
                if (level.getChunk(cp.x, cp.z, ChunkStatus.FULL, false) == null) missing++;
            }

            if (missing > 0) {
                if (job.timeout % 40 == 0) {
                    MeteorShower.LOGGER.info("[MeteorServerManager] Waiting for chunks for [{}, {}]: {}/{} still incomplete.", 
                            job.source.x, job.source.z, missing, job.forcedChunks.size());
                }
                return;
            }

            if (!job.isEmergency && !serverHasHeadroom(level.getServer())) return;

            int targetY = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(job.source.x, 0, job.source.z)).getY();
            BlockPos impactPos = new BlockPos(job.source.x, targetY - 1, job.source.z);
            Vec3 flightStart = new Vec3(job.source.startX, job.source.startY, job.source.startZ);

            MeteorShower.LOGGER.info("[MeteorServerManager] Chunks bereit. Initialisiere CraterPipeline fuer [{}, {}].", job.source.x, job.source.z);
            job.pipeline = new MeteorImpact.CraterPipeline(flightStart, impactPos, job.source.radius);
        }

        // Phase 2: Getaktete Abarbeitung über mehrere Ticks, um den Server-Tick unter dem Schwellenwert zu halten.
        try {
            if (job.pipeline.step(level)) {
                MeteorShower.LOGGER.info("[MeteorServerManager] Pipeline abgeschlossen fuer [{}, {}].", job.source.x, job.source.z);
                terminateJob(level, data, job);
            }
        } catch (Exception e) {
            MeteorShower.LOGGER.error("[MeteorServerManager] Fatal error during pipeline execution!", e);
            terminateJob(level, data, job);
        }
    }

    private static void terminateJob(ServerLevel level, MeteorEventData data, ImpactJob job) {
        data.pendingImpacts.remove(job.source);
        data.setDirty();
        cleanupJob(level);
        nextEligibleTick = level.getServer().getTickCount() + COOLDOWN_TICKS;
    }

    private static void cleanupJob(ServerLevel level) {
        if (currentJob == null) return;
        
        for (ChunkPos cp : currentJob.forcedChunks) level.setChunkForced(cp.x, cp.z, false);
        
        if (pausedDhForThisJob) {
            setDHPauseState(false);
            pausedDhForThisJob = false;
        }
        
        currentJob = null;
    }

    private static void startJob(ServerLevel level, PendingImpact target, boolean isEmergency) {
        if (isDistantHorizonsOverloaded()) {
            setDHPauseState(true);
            pausedDhForThisJob = true;
        }
        
        double craterRadius = target.radius * 2.7 + 2.0;
        double simulationRadius = Math.max((craterRadius * 2.7 + 2.0) * 4.0 + 8.0, Math.max(48.0, craterRadius * 7.0));
        int chunkRange = (int) Math.ceil(simulationRadius / 16.0);

        List<ChunkPos> forced = new ArrayList<>();
        int centerCX = target.x >> 4;
        int centerCZ = target.z >> 4;

        for (int cx = centerCX - chunkRange; cx <= centerCX + chunkRange; cx++) {
            for (int cz = centerCZ - chunkRange; cz <= centerCZ + chunkRange; cz++) {
                level.setChunkForced(cx, cz, true);
                forced.add(new ChunkPos(cx, cz));
            }
        }

        currentJob = new ImpactJob(target, forced, isEmergency);
    }

    private static boolean triggerMeteor(ServerLevel level, MeteorEventData data) {
        if (!mapScanned) {
            scanMapForExistingChunksAsync(level);
            return false;
        }
        if (VALID_CHUNKS.isEmpty()) return false;

        long randomChunkLong = VALID_CHUNKS.getLong(level.random.nextInt(VALID_CHUNKS.size()));
        int chunkX = ChunkPos.getX(randomChunkLong);
        int chunkZ = ChunkPos.getZ(randomChunkLong);
        int targetX = chunkX * 16 + level.random.nextInt(16);
        int targetZ = chunkZ * 16 + level.random.nextInt(16);

        int targetY = level.hasChunk(chunkX, chunkZ) 
                ? level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(targetX, 0, targetZ)).getY() 
                : 70;

        double roll = level.random.nextDouble();
        double meteorRadius = roll < 0.65 ? (1.4 + level.random.nextDouble() * 1.5)
                            : roll < 0.93 ? (2.4 + level.random.nextDouble() * 2.0)
                            : (4.0 + level.random.nextDouble() * 2.5);

        double startOffsetX = (level.random.nextBoolean() ? 1 : -1) * (600 + level.random.nextInt(900));
        double startOffsetZ = (level.random.nextBoolean() ? 1 : -1) * (600 + level.random.nextInt(900));
        double startOffsetY = 300 + level.random.nextInt(300);

        Vec3 start = new Vec3(targetX + startOffsetX, targetY + startOffsetY, targetZ + startOffsetZ);
        Vec3 end = new Vec3(targetX, targetY, targetZ);

        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new MeteorSpawnPacket(start, end, FLIGHT_DURATION));

        long readyTick = level.getServer().getTickCount() + FLIGHT_DURATION;
        data.pendingImpacts.add(new PendingImpact(targetX, targetZ, meteorRadius, start.x, start.y, start.z, readyTick));
        return true;
    }

    public static void forceSpawnSingleMeteor(ServerLevel level, BlockPos targetPos) {
        double meteorRadius = 4.0;
        long readyTick = level.getServer().getTickCount() + FLIGHT_DURATION;

        Vec3 start = new Vec3(targetPos.getX() + 600, targetPos.getY() + 300, targetPos.getZ() + 600);
        Vec3 end = Vec3.atCenterOf(targetPos);
        
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new MeteorSpawnPacket(start, end, FLIGHT_DURATION));

        MeteorEventData data = getOrCreateData(level);
        data.pendingImpacts.add(new PendingImpact(targetPos.getX(), targetPos.getZ(), meteorRadius, start.x, start.y, start.z, readyTick));
        data.setDirty();
    }

    public static void resetAndStartEvent(ServerLevel level) {
        MeteorEventData data = getOrCreateData(level);
        data.isFinished = false;
        data.totalMeteorsToSpawn = 30; 
        data.meteorsSpawnedSoFar = 0;
        data.pendingImpacts.clear();
        data.setDirty();

        eventStarted = true;
        spawnTimer = 20;

        cleanupJob(level); 
        VALID_CHUNKS.clear();
        mapScanned = false;
        MeteorShower.LOGGER.info("[MeteorServerManager] Event manually reset.");
    }

    private static void scanMapForExistingChunksAsync(ServerLevel level) {
        if (mapScanned) return;
        mapScanned = true;

        Path worldDir = level.getServer().getWorldPath(LevelResource.LEVEL_DATA_FILE).getParent();
        Path regionDir = worldDir.resolve("region");
        if (!Files.exists(regionDir)) return;

        CompletableFuture.runAsync(() -> {
            LongArrayList tempChunks = new LongArrayList();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(regionDir, "r.*.*.mca")) {
                for (Path mcaFile : stream) {
                    String[] parts = mcaFile.getFileName().toString().split("\\.");
                    if (parts.length != 4) continue;
                    int regionX = Integer.parseInt(parts[1]);
                    int regionZ = Integer.parseInt(parts[2]);

                    try (RandomAccessFile raf = new RandomAccessFile(mcaFile.toFile(), "r")) {
                        for (int i = 0; i < 1024; i++) {
                            // Ein Header-Offset > 0 signalisiert gespeicherte Sektoren, ohne die NBT-Payload zu dekomprimieren.
                            if (raf.readInt() != 0) {
                                tempChunks.add(ChunkPos.asLong(regionX * 32 + (i % 32), regionZ * 32 + (i / 32)));
                            }
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                MeteorShower.LOGGER.error("[MeteorServerManager] Region scan read error!", e);
            }

            level.getServer().execute(() -> {
                VALID_CHUNKS.clear();
                VALID_CHUNKS.addAll(tempChunks);

                MeteorEventData data = getOrCreateData(level);
                int calculated = (int) Math.round((VALID_CHUNKS.size() / 100.0) * MeteorConfig.meteorsPer100Chunks);
                data.totalMeteorsToSpawn = Math.max(MeteorConfig.minMeteors, Math.min(MeteorConfig.maxMeteors, calculated));
                data.setDirty();
                MeteorShower.LOGGER.info("[MeteorServerManager] Region scan complete: {} chunks detected. Target: {}", VALID_CHUNKS.size(), data.totalMeteorsToSpawn);
            });
        });
    }

    private static boolean serverHasHeadroom(net.minecraft.server.MinecraftServer server) {
        return server.getAverageTickTime() < HEADROOM_THRESHOLD_MS;
    }

    private static boolean isDistantHorizonsOverloaded() {
        return ModList.get().isLoaded("distanthorizons") && net.kb150.meteorshower.compat.DistantHorizonsWrapper.isOverloaded();
    }

    private static void setDHPauseState(boolean pause) {
        if (ModList.get().isLoaded("distanthorizons")) {
            net.kb150.meteorshower.compat.DistantHorizonsWrapper.setReadOnly(pause);
        }
    }

    private static PendingImpact pickNext(ServerLevel level, MeteorEventData data, long tick) {
        PendingImpact fallback = null;
        for (PendingImpact p : data.pendingImpacts) {
            if (tick < p.readyTick) continue; 
            if (fallback == null) fallback = p;
            if (allChunksLoaded(level, p)) return p;
        }
        return fallback;
    }

    private static boolean allChunksLoaded(ServerLevel level, PendingImpact p) {
        double craterRadius = p.radius * 2.7 + 2.0;
        double simulationRadius = Math.max((craterRadius * 2.7 + 2.0) * 4.0 + 8.0, Math.max(48.0, craterRadius * 7.0));
        int chunkRange = (int) Math.ceil(simulationRadius / 16.0);
        int centerCX = p.x >> 4;
        int centerCZ = p.z >> 4;

        for (int cx = centerCX - chunkRange; cx <= centerCX + chunkRange; cx++) {
            for (int cz = centerCZ - chunkRange; cz <= centerCZ + chunkRange; cz++) {
                if (level.getChunk(cx, cz, ChunkStatus.FULL, false) == null) return false;
            }
        }
        return true;
    }

    private static MeteorEventData getOrCreateData(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(MeteorEventData::new, MeteorEventData::new, "arnis_meteor_event");
    }

    private static class ImpactJob {
        final PendingImpact source;
        final List<ChunkPos> forcedChunks;
        final boolean isEmergency;
        int timeout = 1200; 
        MeteorImpact.CraterPipeline pipeline = null;

        ImpactJob(PendingImpact source, List<ChunkPos> forcedChunks, boolean isEmergency) {
            this.source = source;
            this.forcedChunks = forcedChunks;
            this.isEmergency = isEmergency;
        }
    }

    public static class PendingImpact {
        public final int x, z;
        public final double radius;
        public final double startX, startY, startZ;
        public final long readyTick; 

        public PendingImpact(int x, int z, double radius, double sx, double sy, double sz, long readyTick) {
            this.x = x; this.z = z; this.radius = radius;
            this.startX = sx; this.startY = sy; this.startZ = sz;
            this.readyTick = readyTick;
        }

        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("X", x); t.putInt("Z", z); t.putDouble("Radius", radius);
            t.putDouble("SX", startX); t.putDouble("SY", startY); t.putDouble("SZ", startZ);
            return t;
        }

        public static PendingImpact load(CompoundTag tag) {
            return new PendingImpact(
                tag.getInt("X"), tag.getInt("Z"), tag.getDouble("Radius"),
                tag.getDouble("SX"), tag.getDouble("SY"), tag.getDouble("SZ"),
                0 
            );
        }
    }

    public static class MeteorEventData extends SavedData {
        public boolean isFinished = false;
        public int totalMeteorsToSpawn = 30;
        public int meteorsSpawnedSoFar = 0;
        public final List<PendingImpact> pendingImpacts = new ArrayList<>();

        public MeteorEventData() {}

        public MeteorEventData(CompoundTag tag) {
            this.isFinished = tag.getBoolean("IsFinished");
            this.totalMeteorsToSpawn = tag.getInt("TotalMeteors");
            this.meteorsSpawnedSoFar = tag.getInt("SpawnedMeteors");

            if (tag.contains("PendingImpacts")) {
                ListTag list = tag.getList("PendingImpacts", 10);
                for (int i = 0; i < list.size(); i++) {
                    pendingImpacts.add(PendingImpact.load(list.getCompound(i)));
                }
            }
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putBoolean("IsFinished", this.isFinished);
            tag.putInt("TotalMeteors", this.totalMeteorsToSpawn);
            tag.putInt("SpawnedMeteors", this.meteorsSpawnedSoFar);

            ListTag list = new ListTag();
            for (PendingImpact p : pendingImpacts) list.add(p.save());
            tag.put("PendingImpacts", list);
            return tag;
        }
    }
}