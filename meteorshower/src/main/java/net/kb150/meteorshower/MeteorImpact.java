package net.kb150.meteorshower;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class MeteorImpact {

    private MeteorImpact() {
    }

    public static void execute(
            ServerLevel level,
            Vec3 startPos,
            BlockPos centerPos,
            int meteorIndex
    ) {
        var random = level.random;

        Vec3 impact = Vec3.atCenterOf(centerPos);
        Vec3 direction = impact.subtract(startPos).normalize();

        Vec3 horizontalDirection = new Vec3(direction.x, 0.0, direction.z);
        if (horizontalDirection.lengthSqr() < 0.001) {
            horizontalDirection = new Vec3(1, 0, 0);
        } else {
            horizontalDirection = horizontalDirection.normalize();
        }

        Vec3 sideDirection = new Vec3(-horizontalDirection.z, 0, horizontalDirection.x);

        /*
         * ---------------------------------------------------------
         * METEORGRÖSSE & GEOMETRIE
         * ---------------------------------------------------------
         */
        double meteorRadius;
        double roll = random.nextDouble();

        if (roll < 0.65) {
            meteorRadius = 1.4 + random.nextDouble() * 1.5;
        } else if (roll < 0.93) {
            meteorRadius = 2.4 + random.nextDouble() * 2.0;
        } else {
            meteorRadius = 4.0 + random.nextDouble() * 2.5;
        }

        double craterRadius = meteorRadius * 2.7 + 2.0;
        double craterDepth = meteorRadius * 1.15 + 1.5;
        double ejectaRadius = craterRadius * (2.5 + random.nextDouble() * 1.5);
        double thermalRadius = craterRadius * 3.5;

        double treeImpactRadius = Math.max(48.0, thermalRadius * 2.0);
        double simulationRadius = Math.max(ejectaRadius + 8.0, treeImpactRadius);
        int chunkRange = (int) Math.ceil(simulationRadius / 16.0);
        ChunkPos centerChunk = new ChunkPos(centerPos);

        /*
         * ---------------------------------------------------------
         * CHUNKS LADEN
         * ---------------------------------------------------------
         */
        for (int cx = centerChunk.x - chunkRange; cx <= centerChunk.x + chunkRange; cx++) {
            for (int cz = centerChunk.z - chunkRange; cz <= centerChunk.z + chunkRange; cz++) {
                level.setChunkForced(cx, cz, true);
                level.getChunk(cx, cz);
            }
        }

        try {
            // 0. Baum-/Vegetations-Impact. Das passiert vor dem Terrain-Einschlag,
            // damit Baumstämme und Kronen nicht erst im Kraterverfahren verschwinden.
            List<BlockPos> placedBlocks = new ArrayList<>();
            TreeImpactHandler.applyImpact(
                    level,
                    centerPos,
                    horizontalDirection,
                    meteorRadius,
                    craterRadius,
                    treeImpactRadius,
                    placedBlocks
            );

            // 1. Wasser-Prüfung: Verhindert Trockenlegungs- und Strömungsglitches
            if (isWaterHit(level, centerPos)) {
                handleWaterImpact(level, centerPos, meteorRadius);
                settlePlacedBlocks(level, placedBlocks);
                return;
            }

            // 2. Krater ausgraben
            List<ExcavatedBlock> excavated = excavateCrater(
                    level,
                    centerPos,
                    horizontalDirection,
                    sideDirection,
                    craterRadius,
                    craterDepth
            );

            // 2. Schmelzkern (inkl. seltener Belohnung in der Mitte)
            createMoltenCore(
                    level,
                    centerPos,
                    meteorRadius
            );

            // 4. Ejecta-Simulation
            simulateEjecta(
                    level,
                    centerPos,
                    direction,
                    horizontalDirection,
                    sideDirection,
                    craterRadius,
                    ejectaRadius,
                    excavated,
                    placedBlocks
            );

            // 5. Kraterrand aufschütten
            createCraterRim(
                    level,
                    centerPos,
                    craterRadius,
                    meteorRadius,
                    placedBlocks
            );

            // 6. Thermische Zone (Brand, Verbrennung)
            createThermalZone(
                    level,
                    centerPos,
                    craterRadius,
                    thermalRadius
            );

            // 7. Meteoritenfragmente
            createMeteorFragments(
                    level,
                    centerPos,
                    craterRadius,
                    meteorRadius,
                    placedBlocks
            );

            // 8. Separater Höhlenkollaps: Radialer Schockwellen-Kollaps instabiler Höhlendecken
            collapseSubsurfaceCaves(
                    level,
                    centerPos,
                    craterRadius,
                    craterDepth
            );

            // 9. FINAL: Meteor-Block-Settlement. Erst jetzt, weil der Höhlenkollaps
            // oder die Baumphysik zuvor noch neue schwebende Blöcke erzeugen kann.
            settlePlacedBlocks(level, placedBlocks);

            // Soundeffekt
            level.playSound(
                    null,
                    centerPos,
                    SoundEvents.GENERIC_EXPLODE,
                    SoundSource.AMBIENT,
                    Math.min(12.0F, 4.0F + (float) meteorRadius * 1.5F),
                    0.65F + random.nextFloat() * 0.2F
            );

        } finally {
            for (int cx = centerChunk.x - chunkRange; cx <= centerChunk.x + chunkRange; cx++) {
                for (int cz = centerChunk.z - chunkRange; cz <= centerChunk.z + chunkRange; cz++) {
                    level.setChunkForced(cx, cz, false);
                }
            }
        }
    }

    private static List<ExcavatedBlock> excavateCrater(
            ServerLevel level,
            BlockPos centerPos,
            Vec3 horizontalDirection,
            Vec3 sideDirection,
            double craterRadius,
            double craterDepth
    ) {
        var random = level.random;
        List<ExcavatedBlock> excavated = new ArrayList<>();

        int minX = (int) Math.floor(centerPos.getX() - craterRadius - 2);
        int maxX = (int) Math.ceil(centerPos.getX() + craterRadius + 2);
        int minZ = (int) Math.floor(centerPos.getZ() - craterRadius - 2);
        int maxZ = (int) Math.ceil(centerPos.getZ() + craterRadius + 2);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double dx = x + 0.5 - centerPos.getX();
                double dz = z + 0.5 - centerPos.getZ();
                Vec3 horizontal = new Vec3(dx, 0, dz);
                double distance = horizontal.length();

                if (distance > craterRadius + 2.0) {
                    continue;
                }

                double forward = horizontal.dot(horizontalDirection);
                double side = horizontal.dot(sideDirection);

                double angleStretch = 1.0 + Math.max(0.0, forward / craterRadius) * 0.35;
                double effectiveDistance = Math.sqrt(Math.pow(forward / angleStretch, 2) + side * side);

                double noise = 0.88 + noise01(x * 2, z * 2) * 0.24;
                double localRadius = craterRadius * noise;

                if (effectiveDistance > localRadius) {
                    continue;
                }

                double normalized = Math.min(1.0, effectiveDistance / localRadius);
                double profile = 1.0 - normalized * normalized;

                double localDepth = craterDepth * Math.pow(profile, 0.72) * (0.90 + noise01(x * 7, z * 11) * 0.20);

                int surfaceY = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z)).getY();
                int bedrockFloor = findBedrockFloor(level, x, z, surfaceY);
                int floorY = Math.max(bedrockFloor, (int) Math.floor(surfaceY - localDepth));

                for (int y = surfaceY; y >= floorY; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);

                    if (state.isAir()) {
                        continue;
                    }

                    if (!canMeteorDestroy(level, pos)) {
                        continue;
                    }

                    double depthRatio = (double) (surfaceY - y) / Math.max(1, surfaceY - floorY);
                    double ejectProbability = 0.25 + depthRatio * 0.65;

                    if (random.nextDouble() < ejectProbability) {
                        excavated.add(new ExcavatedBlock(state, x, y, z, depthRatio));
                    }

                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        return excavated;
    }

    private static void simulateEjecta(
            ServerLevel level,
            BlockPos centerPos,
            Vec3 impactDirection,
            Vec3 horizontalDirection,
            Vec3 sideDirection,
            double craterRadius,
            double ejectaRadius,
            List<ExcavatedBlock> excavated,
            List<BlockPos> placedBlocks
    ) {
        var random = level.random;

        for (ExcavatedBlock block : excavated) {
            MaterialPhysics material = MaterialPhysics.analyze(
                    level,
                    new BlockPos(block.x, block.y, block.z),
                    block.state
            );

            double depthEnergy = 0.35 + block.depthRatio * 0.90;
            double materialEnergy = 0.45 + material.mobility * 0.95;
            double fragmentationBoost = 0.75 + material.fragmentation * 0.55;
            double cohesionPenalty = 1.0 - material.cohesion * 0.30;
            double randomEnergy = 0.75 + random.nextDouble() * 0.65;

            double energy = depthEnergy * materialEnergy * fragmentationBoost * cohesionPenalty * randomEnergy;

            double distance = craterRadius + Math.pow(random.nextDouble(), 1.65) * (ejectaRadius - craterRadius) * energy;

            if (material.cohesion > 0.65 && random.nextDouble() < 0.08) {
                distance *= 1.25 + random.nextDouble() * 0.75;
            }

            double angle = random.nextDouble() * Math.PI * 2.0;
            Vec3 radial = horizontalDirection.scale(Math.cos(angle)).add(sideDirection.scale(Math.sin(angle)));

            double lateral = material.lateralSpread * (random.nextDouble() - 0.5);
            Vec3 ejectDirection = horizontalDirection.scale(1.0 - material.lateralSpread * 0.35)
                    .add(radial.scale(0.65 + lateral))
                    .add(impactDirection.scale(0.35));

            if (ejectDirection.lengthSqr() < 0.001) {
                ejectDirection = radial;
            }

            ejectDirection = ejectDirection.normalize();

            int targetX = (int) Math.round(centerPos.getX() + ejectDirection.x * distance);
            int targetZ = (int) Math.round(centerPos.getZ() + ejectDirection.z * distance);

            int scatterCount = 1;
            if (material.fragmentation > 0.70) {
                scatterCount += random.nextInt(2);
            }

            for (int fragment = 0; fragment < scatterCount; fragment++) {
                int finalX = targetX;
                int finalZ = targetZ;

                if (fragment > 0) {
                    int spread = 1 + random.nextInt(2);
                    finalX += random.nextInt(-spread, spread + 1);
                    finalZ += random.nextInt(-spread, spread + 1);
                }

                int surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(finalX, 0, finalZ)).getY();
                BlockPos landing = new BlockPos(finalX, surface, finalZ);

                if (!canMeteorDestroy(level, landing)) {
                    continue;
                }

                double deposition = 1.0 - Math.min(1.0, distance / ejectaRadius);
                double placementChance = (0.08 + deposition * 0.82) * (0.75 + material.fragmentation * 0.35);

                if (random.nextDouble() > placementChance) {
                    continue;
                }

                BlockState stateToPlace = block.state;

                if (material.fragmentation > 0.72 && random.nextDouble() < 0.18) {
                    Block outer = MeteorConfig.getRandomBlock(MeteorConfig.OUTER_SHELL, random);
                    if (outer != null) {
                        stateToPlace = outer.defaultBlockState();
                    }
                }

                level.setBlock(landing, stateToPlace, 3);
                placedBlocks.add(landing);
            }
        }
    }

    private static void createMoltenCore(
            ServerLevel level,
            BlockPos centerPos,
            double meteorRadius
    ) {
        var random = level.random;

        boolean shouldSpawnRare = random.nextInt(Math.max(1, MeteorConfig.RARE_BLOCK_INTERVAL)) == 0;
        boolean rarePlaced = false;

        int radius = Math.max(1, (int) Math.round(meteorRadius * 0.9));

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {

                if (Math.sqrt(x * x + z * z) > radius) {
                    continue;
                }

                int currentX = centerPos.getX() + x;
                int currentZ = centerPos.getZ() + z;

                int surfaceY = level.getHeightmapPos(
                        Heightmap.Types.WORLD_SURFACE,
                        new BlockPos(currentX, 0, currentZ)
                ).getY();

                BlockPos pos = new BlockPos(currentX, surfaceY - 1, currentZ);

                if (!canMeteorDestroy(level, pos)) {
                    continue;
                }

                // Zentraler exklusiver Spawner an (x=0, z=0)
                if (shouldSpawnRare && !rarePlaced && x == 0 && z == 0) {
                    MeteorConfig.spawnRareCoreReward(level, pos, random);
                    rarePlaced = true;
                } else if (random.nextDouble() < 0.35) {
                    BlockState material = MeteorConfig.getRandomBlock(MeteorConfig.CORE_BLOCKS, random).defaultBlockState();
                    level.setBlock(pos, material, 3);
                } else {
                    level.setBlock(pos, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void createCraterRim(
            ServerLevel level,
            BlockPos centerPos,
            double craterRadius,
            double meteorRadius,
            List<BlockPos> placedBlocks
    ) {
        var random = level.random;
        int rimRange = (int) Math.ceil(craterRadius + 3);

        for (int x = -rimRange; x <= rimRange; x++) {
            for (int z = -rimRange; z <= rimRange; z++) {

                double distance = Math.sqrt(x * x + z * z);
                if (distance < craterRadius * 0.82 || distance > craterRadius * 1.18) {
                    continue;
                }

                if (random.nextDouble() > 0.55) {
                    continue;
                }

                double ringNoise = 0.5 + noise01(centerPos.getX() + x, centerPos.getZ() + z);
                int surfaceY = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(centerPos.getX() + x, 0, centerPos.getZ() + z)).getY();

                int height = Math.max(1, (int) Math.round(
                        (1.0 - Math.abs(distance - craterRadius) / (craterRadius * 0.18))
                                * meteorRadius * 0.55 * ringNoise
                ));

                for (int h = 0; h < height; h++) {
                    BlockPos pos = new BlockPos(centerPos.getX() + x, surfaceY + h, centerPos.getZ() + z);
                    BlockState current = level.getBlockState(pos);

                    if (!current.isAir() || !canMeteorDestroy(level, pos)) {
                        continue;
                    }

                    BlockState rimMaterial;
                    double roll = random.nextDouble();

                    if (roll < 0.40) {
                        rimMaterial = MeteorConfig.getRandomBlock(MeteorConfig.OUTER_SHELL, random).defaultBlockState();
                    } else if (roll < 0.75) {
                        rimMaterial = Blocks.STONE.defaultBlockState();
                    } else {
                        rimMaterial = Blocks.COARSE_DIRT.defaultBlockState();
                    }

                    level.setBlock(pos, rimMaterial, 3);
                    placedBlocks.add(pos);
                }
            }
        }
    }

    private static void createThermalZone(
            ServerLevel level,
            BlockPos centerPos,
            double craterRadius,
            double thermalRadius
    ) {
        var random = level.random;
        int range = (int) Math.ceil(thermalRadius);

        for (int x = -range; x <= range; x++) {
            for (int z = -range; z <= range; z++) {

                double distance = Math.sqrt(x * x + z * z);
                if (distance <= craterRadius || distance > thermalRadius) {
                    continue;
                }

                double probability = Math.pow(1.0 - (distance - craterRadius) / (thermalRadius - craterRadius), 2.4);
                if (random.nextDouble() > probability) {
                    continue;
                }

                int xPos = centerPos.getX() + x;
                int zPos = centerPos.getZ() + z;

                int surfaceY = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(xPos, 0, zPos)).getY();
                BlockPos surface = new BlockPos(xPos, surfaceY - 1, zPos);

                if (!canMeteorDestroy(level, surface)) {
                    continue;
                }

                BlockState state = level.getBlockState(surface);

                if (state.is(BlockTags.LEAVES)
                        || state.is(BlockTags.FLOWERS)
                        || state.is(Blocks.GRASS)
                        || state.is(Blocks.TALL_GRASS)
                        || state.is(Blocks.VINE)) {
                    level.setBlock(surface, Blocks.AIR.defaultBlockState(), 3);
                    continue;
                }

                if (state.is(Blocks.GRASS_BLOCK)) {
                    if (random.nextDouble() < 0.65) {
                        level.setBlock(surface, Blocks.COARSE_DIRT.defaultBlockState(), 3);
                    }

                    if (random.nextDouble() < probability * 0.35 && level.isEmptyBlock(surface.above())) {
                        level.setBlock(surface.above(), Blocks.FIRE.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private static void createMeteorFragments(
            ServerLevel level,
            BlockPos centerPos,
            double craterRadius,
            double meteorRadius,
            List<BlockPos> placedBlocks
    ) {
        var random = level.random;
        int fragmentCount = 2 + random.nextInt(Math.max(2, (int) (meteorRadius * 2.5)));

        for (int i = 0; i < fragmentCount; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = random.nextDouble() * craterRadius * 1.4;

            int x = centerPos.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = centerPos.getZ() + (int) Math.round(Math.sin(angle) * distance);
            int y = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z)).getY();

            BlockPos fragmentPos = new BlockPos(x, y, z);

            if (!level.getBlockState(fragmentPos).isAir() || !canMeteorDestroy(level, fragmentPos)) {
                continue;
            }

            Block fragment = MeteorConfig.getRandomBlock(MeteorConfig.CORE_BLOCKS, random);
            if (fragment != null) {
                level.setBlock(fragmentPos, fragment.defaultBlockState(), 3);
                placedBlocks.add(fragmentPos);
            }
        }
    }

    /**
     * MECHANIK 1: Meteor-Block-Settlement
     * Prüft ausschließlich Blöcke, die tatsächlich durch Ejecta, Fragmente
     * oder den Kraterrand erzeugt wurden. Hängen sie in der Luft (z. B. auf verbranntem Laub),
     * sacken sie auf festen Boden nach.
     */
    private static void settlePlacedBlocks(ServerLevel level, List<BlockPos> placedBlocks) {
        // Von unten nach oben sortieren, damit übereinanderliegende Blöcke in der
        // richtigen Reihenfolge stabilisiert werden.
        placedBlocks.sort((a, b) -> Integer.compare(a.getY(), b.getY()));

        for (BlockPos pos : placedBlocks) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }

            BlockPos target = findDropTarget(level, pos, 64);
            if (target.equals(pos)) {
                continue;
            }

            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(target, state, 3);
        }
    }

    /**
     * MECHANIK 2: Echter, physikalischer Höhlenkollaps (Schockwelle & Statik)
     * Rührt keine unbetroffenen Blöcke an! Untersucht ausschließlich reale Höhlendecken
     * (Gestein direkt über Hohlräumen) und berechnet deren Stabilität anhand:
     * - Radialer Schockwellenenergie (0.0 = Kraterzentrum, 1.0 = Rand)
     * - Überdeckung / Deckenstärke (Dünne Decken brechen, dicker Fels hält stand)
     * - Materialhärte der Decke
     */
    private static void collapseSubsurfaceCaves(
            ServerLevel level,
            BlockPos centerPos,
            double craterRadius,
            double craterDepth
    ) {
        var random = level.random;

        double collapseRadius = craterRadius * 1.85;
        double radiusSq = collapseRadius * collapseRadius;

        int minX = (int) Math.floor(centerPos.getX() - collapseRadius);
        int maxX = (int) Math.ceil(centerPos.getX() + collapseRadius);
        int minZ = (int) Math.floor(centerPos.getZ() - collapseRadius);
        int maxZ = (int) Math.ceil(centerPos.getZ() + collapseRadius);

        int startY = centerPos.getY() - (int) craterDepth;
        int bottomY = Math.max(level.getMinBuildHeight() + 1, startY - 35);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {

                double dx = x + 0.5 - centerPos.getX();
                double dz = z + 0.5 - centerPos.getZ();
                double distSq = dx * dx + dz * dz;

                if (distSq > radiusSq) {
                    continue;
                }

                // Organische Randabschwächung
                double edgeNoise = 0.82 + noise01(x * 5, z * 5) * 0.32;
                if (distSq > radiusSq * edgeNoise) {
                    continue;
                }

                double horizontalDist = Math.sqrt(distSq);
                double normDist = horizontalDist / collapseRadius; // 0.0 bis 1.0

                // Schockwellen-Intensität
                double shockEnergy = Math.pow(1.0 - normDist, 1.3) * (0.85 + noise01(x * 9, z * 9) * 0.30);
                if (shockEnergy < 0.12) {
                    continue;
                }

                int surfaceY = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z)).getY();

                // Scan von knapp unter dem Kraterboden nach unten durch Höhlensysteme
                for (int y = startY; y >= bottomY; y--) {
                    BlockPos ceilingPos = new BlockPos(x, y, z);
                    BlockState ceilingState = level.getBlockState(ceilingPos);

                    // Wir suchen gezielt Höhlendecken: Fester Block direkt über einem Hohlraum
                    if (ceilingState.isAir() || !canMeteorDestroy(level, ceilingPos)) {
                        continue;
                    }

                    if (!level.isEmptyBlock(ceilingPos.below())) {
                        continue;
                    }

                    // --- STATIK-PRÜFUNG ---
                    // 1. Überdeckung: Wie viel Gestein/Boden liegt zwischen Oberfläche und Höhlendecke?
                    int overburden = Math.max(1, surfaceY - y);
                    double overburdenFactor = Math.min(1.0, overburden / 22.0); // Ab 22 Blöcken Felsüberdeckung sehr stabil

                    // 2. Materialhärte der Decke
                    double hardness = Math.min(1.0, ceilingState.getDestroySpeed(level, ceilingPos) / 3.0);

                    // Instabilität: Hohe Schockenergie + dünne Überdeckung + weicheres Gestein = Bruch
                    double instability = shockEnergy * (1.15 - overburdenFactor * 0.75) * (1.10 - hardness * 0.35);

                    if (random.nextDouble() > instability) {
                        continue; // Höhlendecke bleibt stabil
                    }

                    // --- KONTROLLIERTER BRUCH DER DECKE ---
                    // Es bricht nur die unmittelbare Decke (1-3 Blöcke) aus und stürzt als Geröll ab
                    int ruptureThickness = Math.max(1, (int) Math.round(3.0 * shockEnergy));

                    for (int r = 0; r < ruptureThickness; r++) {
                        BlockPos targetPos = ceilingPos.above(r);
                        BlockState blockToDrop = level.getBlockState(targetPos);

                        if (blockToDrop.isAir() || !canMeteorDestroy(level, targetPos)) {
                            break;
                        }

                        // Höhlenboden direkt darunter suchen
                        BlockPos floorTarget = findDropTarget(level, targetPos, 25);
                        if (floorTarget.equals(targetPos)) {
                            continue;
                        }

                        // Decke aushöhlen
                        level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), 3);

                        // Bruchstein/Geröll am Höhlenboden ablagern
                        BlockState rubbleState = blockToDrop;
                        if (blockToDrop.is(Blocks.STONE) || blockToDrop.is(Blocks.DEEPSLATE)) {
                            rubbleState = random.nextBoolean() ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
                        } else if (blockToDrop.is(Blocks.DIRT) || blockToDrop.is(Blocks.GRASS_BLOCK)) {
                            rubbleState = Blocks.COARSE_DIRT.defaultBlockState();
                        }

                        level.setBlock(floorTarget, rubbleState, 3);
                    }

                    // Überspringe die ausgebrochene Schicht
                    y -= ruptureThickness;
                }
            }
        }
    }

    /**
     * Sucht für einen bereits platzierten oder herabfallenden Block nach
     * einer physikalisch tragfähigen Oberfläche.
     *
     * Die Entscheidung, ob ein Block trägt oder nicht, liegt vollständig in
     * MaterialPhysics. Dadurch gibt es hier keine Sonderfälle für Schnee,
     * Blätter, Pflanzen, Wasser oder einzelne Mod-Blöcke.
     */
    private static BlockPos findDropTarget(ServerLevel level, BlockPos startPos, int maxDrop) {
        BlockPos current = startPos;

        for (int dropped = 0; dropped <= maxDrop; dropped++) {
            if (current.getY() <= level.getMinBuildHeight() + 1) {
                return startPos;
            }

            BlockPos below = current.below();
            BlockState belowState = level.getBlockState(below);
            MaterialPhysics support = MaterialPhysics.analyze(level, below, belowState);

            // Nur eine vollständige Kollisionsform gilt als tragfähige
            // Oberfläche. Damit fallen Ejecta nicht dauerhaft auf
            // Schnee-Layern, Blättern oder anderen Teilformen liegen.
            if (support.supportStrength >= 0.99) {
                return below.above();
            }

            // Alles, was keine vollwertige tragende Kollisionsfläche besitzt,
            // wird für das Settlement übersprungen. Das umfasst automatisch
            // z. B. Snow-Layer, Leaves, dünne Vegetation und Flüssigkeiten.
            if (belowState.isAir() || support.supportStrength < 0.99) {
                current = below;
                continue;
            }

            return startPos;
        }

        return startPos;
    }

    private static boolean canMeteorDestroy(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.BEDROCK)
                || state.is(Blocks.BARRIER)
                || state.is(Blocks.END_PORTAL)
                || state.is(Blocks.END_PORTAL_FRAME)) {
            return false;
        }
        return state.getDestroySpeed(level, pos) >= 0;
    }

    private static int findBedrockFloor(ServerLevel level, int x, int z, int surfaceY) {
        for (int y = surfaceY; y >= level.getMinBuildHeight(); y--) {
            if (level.getBlockState(new BlockPos(x, y, z)).is(Blocks.BEDROCK)) {
                return y + 1;
            }
        }
        return level.getMinBuildHeight() + 1;
    }

    private static double noise01(int x, int z) {
        int n = x + z * 57;
        n = (n << 13) ^ n;
        double value = 1.0 - ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / 1073741824.0;
        return (value + 1.0) * 0.5;
    }

    private static class ExcavatedBlock {
        final BlockState state;
        final int x;
        final int y;
        final int z;
        final double depthRatio;

        ExcavatedBlock(BlockState state, int x, int y, int z, double depthRatio) {
            this.state = state;
            this.x = x;
            this.y = y;
            this.z = z;
            this.depthRatio = depthRatio;
        }
    }

    /**
     * Prüft, ob der Einschlagspunkt auf oder in einem Gewässer liegt.
     */
    private static boolean isWaterHit(ServerLevel level, BlockPos centerPos) {
        return level.getFluidState(centerPos).is(FluidTags.WATER)
                || level.getFluidState(centerPos.above()).is(FluidTags.WATER)
                || level.getBlockState(centerPos).is(Blocks.WATER)
                || level.getBlockState(centerPos.above()).is(Blocks.WATER);
    }

    /**
     * Verarbeitet den Wasser-Einschlag:
     * Kein Luft-Krater, kein Rand, kein Höhleneinsturz.
     * Der Meteorit sinkt auf den Grund, formt dort einen kompakten Findling/Kern
     * und erzeugt Zisch-, Spritz- und gedämpfte Unterwasser-Explosionseffekte.
     */
    private static void handleWaterImpact(
            ServerLevel level,
            BlockPos surfacePos,
            double meteorRadius
    ) {
        var random = level.random;

        // Den Meeresgrund / Gewässerboden suchen
        BlockPos seabedPos = surfacePos;
        while (seabedPos.getY() > level.getMinBuildHeight() + 1
                && (level.getFluidState(seabedPos).is(FluidTags.WATER) || level.getBlockState(seabedPos).isAir())) {
            seabedPos = seabedPos.below();
        }

        int meteorIntRadius = Math.max(1, (int) Math.round(meteorRadius));
        boolean shouldSpawnRare = random.nextInt(Math.max(1, MeteorConfig.RARE_BLOCK_INTERVAL)) == 0;
        boolean rarePlaced = false;

        // Meteoriten-Gestein auf dem Grund platzieren (ohne das umliegende Wasser zu entfernen)
        for (int x = -meteorIntRadius; x <= meteorIntRadius; x++) {
            for (int y = -meteorIntRadius; y <= meteorIntRadius; y++) {
                for (int z = -meteorIntRadius; z <= meteorIntRadius; z++) {

                    double dist = Math.sqrt(x * x + y * y + z * z);
                    double noise = 0.85 + noise01((seabedPos.getX() + x) * 4, (seabedPos.getZ() + z) * 4) * 0.30;
                    if (dist > meteorIntRadius * noise) {
                        continue;
                    }

                    BlockPos target = seabedPos.offset(x, y + (meteorIntRadius / 2), z);
                    if (!canMeteorDestroy(level, target)) {
                        continue;
                    }

                    // Zentrum: Seltene Kern-Belohnung (Drachenei / Spezialblock)
                    if (shouldSpawnRare && !rarePlaced && x == 0 && y == 0 && z == 0) {
                        MeteorConfig.spawnRareCoreReward(level, target, random);
                        rarePlaced = true;
                    } else if (dist < meteorIntRadius * 0.5) {
                        // Innerer Kern (Magmablöcke erzeugen unter Wasser Bläschensäulen)
                        if (random.nextDouble() < 0.35) {
                            Block core = MeteorConfig.getRandomBlock(MeteorConfig.CORE_BLOCKS, random);
                            level.setBlock(target, core.defaultBlockState(), 3);
                        } else {
                            level.setBlock(target, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
                        }
                    } else {
                        // Äußere Schale
                        Block shell = MeteorConfig.getRandomBlock(MeteorConfig.OUTER_SHELL, random);
                        level.setBlock(target, shell != null ? shell.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState(), 3);
                    }
                }
            }
        }

        // Spritzer an der Wasseroberfläche
        level.playSound(
                null,
                surfacePos,
                SoundEvents.GENERIC_SPLASH,
                SoundSource.AMBIENT,
                6.0F,
                0.8F + random.nextFloat() * 0.3F
        );

        // Zischen durch das abkühlende heiße Gestein
        level.playSound(
                null,
                surfacePos,
                SoundEvents.FIRE_EXTINGUISH,
                SoundSource.AMBIENT,
                5.0F,
                0.7F + random.nextFloat() * 0.2F
        );

        // Gedämpfte Unterwasser-Erschütterung am Grund
        level.playSound(
                null,
                seabedPos,
                SoundEvents.GENERIC_EXPLODE,
                SoundSource.AMBIENT,
                Math.min(8.0F, 3.0F + (float) meteorRadius),
                0.50F + random.nextFloat() * 0.15F
        );
    }
}