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
    private static final double HEADROOM_THRESHOLD_MS = 38.0; 
    private static final int COOLDOWN_TICKS = 100; 
    private static final double EMERGENCY_DISTANCE_SQ = 400.0 * 400.0;

    // --- RUNTIME STATE (Flüchtig) ---
    // Startet mit dem Wert aus der Config (-1 = deaktivierter automatischer Start)
    private static int spawnTimer = MeteorConfig.EVENT_START_DELAY;
    private static boolean eventStarted = false;
    private static final LongArrayList VALID_CHUNKS = new LongArrayList();
    private static boolean mapScanned = false;

    private static ImpactJob currentJob = null; 
    private static long nextEligibleTick = 0;
    
    // Snickers-Flag: Haben wir DH für DIESEN EINEN Job pausiert?
    private static boolean pausedDhForThisJob = false; 

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide()) return;
        if (!(event.level instanceof ServerLevel serverLevel)) return;
        if (serverLevel.dimension() != Level.OVERWORLD) return;

        MeteorEventData data = getOrCreateData(serverLevel);
        long tick = serverLevel.getServer().getTickCount();

        // 1. EVENT: Flugshows starten (Kosmetik & Job in die Queue packen)
        handleEventSpawning(serverLevel, data);

        // 2. SCHEDULER: Laufenden Job abarbeiten
        if (currentJob != null) {
            advanceJob(serverLevel, data);
            return; // Während ein Job läuft, NIE einen neuen starten!
        }

        if (data.pendingImpacts.isEmpty()) return;

        // 3. SCHEDULER: Notbremse (Spieler nähert sich einem wartenden Krater)
        if (handleEmergencyTriggers(serverLevel, data, tick)) {
            return; // Ein Notfall-Job wurde gestartet
        }

        // 4. SCHEDULER: Hintergrund-Arbeiter
        handleBackgroundScheduler(serverLevel, data, tick);
    }

    private static void handleEventSpawning(ServerLevel level, MeteorEventData data) {
        if (data.isFinished) return;

        if (!eventStarted) {
            if (!level.players().isEmpty()) {
                // Wenn EVENT_START_DELAY auf -1 (oder negativ) gesetzt ist,
                // zählt der Countdown nicht herunter. Das Event startet nur via Command (/meteor resetEvent).
                if (MeteorConfig.EVENT_START_DELAY < 0) {
                    return;
                }

                spawnTimer--;
                if (spawnTimer <= 0) eventStarted = true;
            }
            return;
        }

        if (data.meteorsSpawnedSoFar < data.totalMeteorsToSpawn) {
            spawnTimer--;
            if (spawnTimer <= 0) {
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
            spawnTimer = MeteorConfig.EVENT_START_DELAY;
        }
    }

    private static boolean handleEmergencyTriggers(ServerLevel level, MeteorEventData data, long tick) {
        for (PendingImpact p : data.pendingImpacts) {
            if (tick < p.readyTick) continue; 

            for (ServerPlayer player : level.players()) {
                double distSq = player.distanceToSqr(p.x, player.getY(), p.z);
                if (distSq <= EMERGENCY_DISTANCE_SQ) {
                    startJob(level, p, true); 
                    return true;
                }
            }
        }
        return false;
    }

    private static void handleBackgroundScheduler(ServerLevel level, MeteorEventData data, long tick) {
        if (tick < nextEligibleTick) return; 
        if (!serverHasHeadroom(level.getServer())) return; 

        PendingImpact best = pickNext(level, data, tick);
        if (best != null) {
            startJob(level, best, false); 
        }
    }

    private static void advanceJob(ServerLevel level, MeteorEventData data) {
        ImpactJob job = currentJob;
        job.timeout--;

        if (job.timeout <= 0) {
            MeteorShower.LOGGER.warn("Meteor-Impact Timeout erreicht! Breche ab.");
            data.pendingImpacts.remove(job.source);
            data.setDirty();
            cleanupJob(level); // cleanupJob hebt auch die DH-Pause wieder auf!
            return;
        }

        boolean allLoaded = true;
        for (ChunkPos cp : job.forcedChunks) {
            if (level.getChunk(cp.x, cp.z, ChunkStatus.FULL, false) == null) {
                allLoaded = false;
                break;
            }
        }

        if (allLoaded) {
            if (!job.isEmergency && !serverHasHeadroom(level.getServer())) {
                return; // Server braucht kurz Luft, wir verschieben um einen Tick
            }

            try {
                int targetY = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(job.source.x, 0, job.source.z)).getY();
                BlockPos impactPos = new BlockPos(job.source.x, targetY - 1, job.source.z);
                Vec3 flightStart = new Vec3(job.source.startX, job.source.startY, job.source.startZ);

                MeteorImpact.execute(level, flightStart, impactPos, 0, job.source.radius);
            } catch (Exception e) {
                MeteorShower.LOGGER.error("Fehler bei MeteorImpact.execute()!", e);
            } finally {
                data.pendingImpacts.remove(job.source);
                data.setDirty();
                
                cleanupJob(level);
                nextEligibleTick = level.getServer().getTickCount() + COOLDOWN_TICKS;
            }
        }
    }

    private static void cleanupJob(ServerLevel level) {
        if (currentJob == null) return;
        
        for (ChunkPos cp : currentJob.forcedChunks) {
            level.setChunkForced(cp.x, cp.z, false);
        }
        
        // DH-Pause aufheben, falls wir sie für diesen einen Job gesetzt hatten
        if (pausedDhForThisJob) {
            setDHPauseState(false);
            pausedDhForThisJob = false;
        }
        
        currentJob = null;
    }

    private static void startJob(ServerLevel level, PendingImpact target, boolean isEmergency) {
        // "Iss ein Snickers"-Logik
        if (isDistantHorizonsOverloaded()) {
            setDHPauseState(true);
            pausedDhForThisJob = true;
        }
        
        double craterRadius = target.radius * 2.7 + 2.0;
        double thermalRadius = craterRadius * 3.5;
        double treeImpactRadius = Math.max(48.0, thermalRadius * 2.0);
        double ejectaRadius = craterRadius * 4.0;
        double simulationRadius = Math.max(ejectaRadius + 8.0, treeImpactRadius);
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

        int targetY = 70;
        if (level.hasChunk(chunkX, chunkZ)) {
            targetY = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(targetX, 0, targetZ)).getY();
        }

        double meteorRadius;
        double roll = level.random.nextDouble();
        if (roll < 0.65) meteorRadius = 1.4 + level.random.nextDouble() * 1.5;
        else if (roll < 0.93) meteorRadius = 2.4 + level.random.nextDouble() * 2.0;
        else meteorRadius = 4.0 + level.random.nextDouble() * 2.5;

        double startOffsetX = (level.random.nextBoolean() ? 1 : -1) * (600 + level.random.nextInt(900));
        double startOffsetZ = (level.random.nextBoolean() ? 1 : -1) * (600 + level.random.nextInt(900));
        double startOffsetY = 300 + level.random.nextInt(300);

        Vec3 start = new Vec3(targetX + startOffsetX, targetY + startOffsetY, targetZ + startOffsetZ);
        Vec3 end = new Vec3(targetX, targetY, targetZ);

        MeteorSpawnPacket packet = new MeteorSpawnPacket(start, end, FLIGHT_DURATION);
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), packet);

        long readyTick = level.getServer().getTickCount() + FLIGHT_DURATION;
        data.pendingImpacts.add(new PendingImpact(targetX, targetZ, meteorRadius, start.x, start.y, start.z, readyTick));
        
        return true;
    }

    public static void forceSpawnSingleMeteor(ServerLevel level, BlockPos targetPos) {
        double meteorRadius = 4.0;
        long readyTick = level.getServer().getTickCount() + FLIGHT_DURATION;

        Vec3 start = new Vec3(targetPos.getX() + 600, targetPos.getY() + 300, targetPos.getZ() + 600);
        Vec3 end = Vec3.atCenterOf(targetPos);
        
        MeteorSpawnPacket packet = new MeteorSpawnPacket(start, end, FLIGHT_DURATION);
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), packet);

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

        // Wird explizit via Befehl gestartet, unabhängig vom Start-Countdown
        eventStarted = true;
        spawnTimer = 20;

        cleanupJob(level); 
        VALID_CHUNKS.clear();
        mapScanned = false;
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
                            if (raf.readInt() == 0) continue;
                            tempChunks.add(ChunkPos.asLong(regionX * 32 + (i % 32), regionZ * 32 + (i / 32)));
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                MeteorShower.LOGGER.error("Fehler beim asynchronen Scannen!", e);
            }

            level.getServer().execute(() -> {
                VALID_CHUNKS.clear();
                VALID_CHUNKS.addAll(tempChunks);

                MeteorEventData data = getOrCreateData(level);
                int calculated = (int) Math.round((VALID_CHUNKS.size() / 100.0) * MeteorConfig.METEORS_PER_100_CHUNKS);
                data.totalMeteorsToSpawn = Math.max(MeteorConfig.MIN_METEORS, Math.min(MeteorConfig.MAX_METEORS, calculated));
                data.setDirty();
            });
        });
    }

    private static boolean serverHasHeadroom(net.minecraft.server.MinecraftServer server) {
        float mspt = server.getAverageTickTime();
        return mspt < HEADROOM_THRESHOLD_MS;
    }

    private static boolean isDistantHorizonsOverloaded() {
        if (ModList.get().isLoaded("distanthorizons")) {
            return net.kb150.meteorshower.compat.DistantHorizonsWrapper.isOverloaded();
        }
        return false;
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
        double simulationRadius = Math.max((p.radius * 2.7 + 2.0) * 4.0 + 8.0, Math.max(48.0, (p.radius * 2.7 + 2.0) * 7.0));
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
            for (PendingImpact p : pendingImpacts) {
                list.add(p.save());
            }
            tag.put("PendingImpacts", list);
            return tag;
        }
    }
}