package net.kb150.meteorshower;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.io.RandomAccessFile;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MeteorServerManager {

    private static final int DELAY_BETWEEN_SPAWNS = 40;
    private static final int FLIGHT_DURATION = 80;

    private static int totalMeteorsToSpawn = 30;
    private static int meteorsSpawnedSoFar = 0;

    private static int spawnTimer = 100;

    private static boolean eventFinished = false;
    private static boolean eventStarted = false;

    private static final List<ActiveMeteor> flyingMeteors =
            new ArrayList<>();

    private static final LongArrayList VALID_CHUNKS =
            new LongArrayList();

    private static boolean mapScanned = false;

    @SubscribeEvent
    public static void onLevelTick(
            TickEvent.LevelTickEvent event
    ) {
        if (
                event.phase != TickEvent.Phase.END
                        || event.level.isClientSide()
        ) {
            return;
        }

        if (!(event.level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (serverLevel.dimension() != Level.OVERWORLD) {
            return;
        }

        MeteorEventData data =
                getOrCreateData(serverLevel);

        if (data.isFinished) {
            return;
        }

        /*
         * ---------------------------------------------------------
         * EVENT START
         * ---------------------------------------------------------
         */

        if (!eventStarted) {

            if (!serverLevel.players().isEmpty()) {

                spawnTimer--;

                if (spawnTimer <= 0) {
                    eventStarted = true;
                }
            }

            return;
        }

        /*
         * ---------------------------------------------------------
         * METEORE SPAWNEN
         * ---------------------------------------------------------
         */

        if (
                meteorsSpawnedSoFar
                        < totalMeteorsToSpawn
        ) {

            spawnTimer--;

            if (spawnTimer <= 0) {

                /*
                 * Nur erhöhen, wenn tatsächlich ein Meteor
                 * erzeugt werden konnte.
                 */
                if (triggerMeteor(serverLevel)) {
                    meteorsSpawnedSoFar++;
                }

                spawnTimer =
                        DELAY_BETWEEN_SPAWNS;
            }

        } else if (flyingMeteors.isEmpty()) {

            data.isFinished = true;
            data.setDirty();

            eventFinished = true;
        }

        /*
         * ---------------------------------------------------------
         * FLIEGENDE METEORE
         * ---------------------------------------------------------
         */

        Iterator<ActiveMeteor> iterator =
                flyingMeteors.iterator();

        while (iterator.hasNext()) {

            ActiveMeteor meteor =
                    iterator.next();

            meteor.ticksUntilImpact--;

            if (meteor.ticksUntilImpact <= 0) {

                MeteorImpact.execute(
                        serverLevel,
                        meteor.startPos,
                        meteor.targetPos,
                        meteor.meteorIndex
                );

                iterator.remove();
            }
        }
    }

    /**
     * Scannt die tatsächlich vorhandenen MCA-Chunks.
     *
     * Es werden keine neuen Chunks erzeugt.
     */
    private static void scanMapForExistingChunks(
            ServerLevel level
    ) {
        VALID_CHUNKS.clear();

        Path worldDir =
                level.getServer()
                        .getWorldPath(
                                LevelResource.LEVEL_DATA_FILE
                        )
                        .getParent();

        Path regionDir =
                worldDir.resolve("region");

        if (!Files.exists(regionDir)) {
            mapScanned = true;
            return;
        }

        try (
                DirectoryStream<Path> stream =
                        Files.newDirectoryStream(
                                regionDir,
                                "r.*.*.mca"
                        )
        ) {

            for (Path mcaFile : stream) {

                String[] parts =
                        mcaFile.getFileName()
                                .toString()
                                .split("\\.");

                if (parts.length != 4) {
                    continue;
                }

                int regionX =
                        Integer.parseInt(parts[1]);

                int regionZ =
                        Integer.parseInt(parts[2]);

                try (
                        RandomAccessFile raf =
                                new RandomAccessFile(
                                        mcaFile.toFile(),
                                        "r"
                                )
                ) {

                    /*
                     * Header:
                     *
                     * 1024 x 4 Bytes
                     *
                     * Ein Eintrag != 0 bedeutet:
                     * dieser Chunk besitzt einen Eintrag
                     * in der Region-Datei.
                     */
                    for (int i = 0;
                         i < 1024;
                         i++) {

                        int offset =
                                raf.readInt();

                        if (offset == 0) {
                            continue;
                        }

                        int localX =
                                i % 32;

                        int localZ =
                                i / 32;

                        int chunkX =
                                regionX * 32
                                        + localX;

                        int chunkZ =
                                regionZ * 32
                                        + localZ;

                        VALID_CHUNKS.add(
                                ChunkPos.asLong(
                                        chunkX,
                                        chunkZ
                                )
                        );
                    }

                } catch (Exception ignored) {
                    /*
                     * Eine beschädigte Einzeldatei soll
                     * nicht den gesamten Meteor-Event
                     * stoppen.
                     */
                }
            }

        } catch (Exception e) {

            MeteorShower.LOGGER.error(
                    "Fehler beim Scannen des Region-Ordners!",
                    e
            );
        }

        mapScanned = true;

        /*
         * ---------------------------------------------------------
         * METEORANZAHL
         * ---------------------------------------------------------
         */

        int calculated =
                (int) Math.round(
                        (
                                VALID_CHUNKS.size()
                                        / 100.0
                        )
                                * MeteorConfig.METEORS_PER_100_CHUNKS
                );

        totalMeteorsToSpawn =
                Math.max(
                        MeteorConfig.MIN_METEORS,
                        Math.min(
                                MeteorConfig.MAX_METEORS,
                                calculated
                        )
                );

        MeteorShower.LOGGER.info(
                "Map-Scan abgeschlossen! "
                        + VALID_CHUNKS.size()
                        + " Chunks gefunden. "
                        + "Generiere "
                        + totalMeteorsToSpawn
                        + " Meteore."
        );
    }

    /**
     * Erzeugt einen Meteor an einem bereits vorhandenen Chunk.
     */
    private static boolean triggerMeteor(
            ServerLevel level
    ) {
        if (!mapScanned) {
            scanMapForExistingChunks(level);
        }

        if (VALID_CHUNKS.isEmpty()) {
            MeteorShower.LOGGER.warn(
                    "Keine erkundeten Chunks für Meteor gefunden."
            );

            return false;
        }

        long randomChunkLong =
                VALID_CHUNKS.getLong(
                        level.random.nextInt(
                                VALID_CHUNKS.size()
                        )
                );

        int chunkX =
                ChunkPos.getX(
                        randomChunkLong
                );

        int chunkZ =
                ChunkPos.getZ(
                        randomChunkLong
                );

        int targetX =
                chunkX * 16
                        + level.random.nextInt(16);

        int targetZ =
                chunkZ * 16
                        + level.random.nextInt(16);

        /*
         * Der Chunk existiert bereits.
         *
         * Trotzdem laden wir ihn kurz explizit, damit
         * Heightmap und Terrain zuverlässig verfügbar sind.
         */
        level.setChunkForced(
                chunkX,
                chunkZ,
                true
        );

        try {

            level.getChunk(
                    chunkX,
                    chunkZ
            );

            int targetY =
                    level.getHeightmapPos(
                            Heightmap.Types.WORLD_SURFACE,
                            new BlockPos(
                                    targetX,
                                    0,
                                    targetZ
                            )
                    ).getY();

            BlockPos impactPos =
                    new BlockPos(
                            targetX,
                            targetY - 1,
                            targetZ
                    );

            /*
             * -----------------------------------------------------
             * FLUGBAHN
             * -----------------------------------------------------
             */

            double startOffsetX =
                    (
                            level.random.nextBoolean()
                                    ? 1
                                    : -1
                    )
                            * (
                            600
                                    + level.random.nextInt(900)
                    );

            double startOffsetZ =
                    (
                            level.random.nextBoolean()
                                    ? 1
                                    : -1
                    )
                            * (
                            600
                                    + level.random.nextInt(900)
                    );

            double startOffsetY =
                    300
                            + level.random.nextInt(300);

            Vec3 start =
                    new Vec3(
                            targetX + startOffsetX,
                            targetY + startOffsetY,
                            targetZ + startOffsetZ
                    );

            Vec3 end =
                    new Vec3(
                            impactPos.getX(),
                            impactPos.getY(),
                            impactPos.getZ()
                    );

            MeteorSpawnPacket packet =
                    new MeteorSpawnPacket(
                            start,
                            end,
                            FLIGHT_DURATION
                    );

            NetworkHandler.INSTANCE.send(
                    PacketDistributor.ALL.noArg(),
                    packet
            );

            flyingMeteors.add(
                    new ActiveMeteor(
                            start,
                            impactPos,
                            FLIGHT_DURATION,
                            meteorsSpawnedSoFar + 1
                    )
            );

            return true;

        } finally {

            level.setChunkForced(
                    chunkX,
                    chunkZ,
                    false
            );
        }
    }

    /**
     * Manueller Test-Meteor.
     */
    public static void forceSpawnSingleMeteor(
            ServerLevel level,
            BlockPos playerPos
    ) {
        int targetY =
                level.getHeightmapPos(
                        Heightmap.Types.WORLD_SURFACE,
                        playerPos
                ).getY();

        BlockPos impactPos =
                new BlockPos(
                        playerPos.getX(),
                        targetY - 1,
                        playerPos.getZ()
                );

        Vec3 start =
                new Vec3(
                        impactPos.getX() + 800,
                        targetY + 300,
                        impactPos.getZ() + 800
                );

        Vec3 end =
                Vec3.atCenterOf(impactPos);

        MeteorSpawnPacket packet =
                new MeteorSpawnPacket(
                        start,
                        end,
                        FLIGHT_DURATION
                );

        NetworkHandler.INSTANCE.send(
                PacketDistributor.ALL.noArg(),
                packet
        );

        flyingMeteors.add(
                new ActiveMeteor(
                        start,
                        impactPos,
                        FLIGHT_DURATION,
                        meteorsSpawnedSoFar + 1
                )
        );
    }

    public static void resetAndStartEvent(
            ServerLevel level
    ) {
        MeteorEventData data =
                getOrCreateData(level);

        data.isFinished = false;
        data.setDirty();

        eventFinished = false;
        eventStarted = true;

        meteorsSpawnedSoFar = 0;
        spawnTimer = 20;

        VALID_CHUNKS.clear();
        mapScanned = false;
    }

    private static MeteorEventData getOrCreateData(
            ServerLevel level
    ) {
        return level.getDataStorage()
                .computeIfAbsent(
                        MeteorEventData::new,
                        MeteorEventData::new,
                        "arnis_meteor_event"
                );
    }

    private static class ActiveMeteor {

        final Vec3 startPos;
        final BlockPos targetPos;

        int ticksUntilImpact;

        final int meteorIndex;

        ActiveMeteor(
                Vec3 startPos,
                BlockPos targetPos,
                int ticksUntilImpact,
                int meteorIndex
        ) {
            this.startPos = startPos;
            this.targetPos = targetPos;
            this.ticksUntilImpact = ticksUntilImpact;
            this.meteorIndex = meteorIndex;
        }
    }

    private static class MeteorEventData
            extends SavedData {

        boolean isFinished = false;

        MeteorEventData() {
        }

        MeteorEventData(CompoundTag tag) {
            this.isFinished =
                    tag.getBoolean(
                            "IsFinished"
                    );
        }

        @Override
        public CompoundTag save(
                CompoundTag tag
        ) {
            tag.putBoolean(
                    "IsFinished",
                    this.isFinished
            );

            return tag;
        }
    }
}