package net.kb150.meteorshower;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class MeteorImpact {

    private MeteorImpact() {}

    public enum PipelineStage {
        PREPARE,
        TREES,
        EXCAVATE,
        EJECTA_AND_DETAILS,
        CAVE_COLLAPSE,
        SETTLE,
        FINALIZE,
        DONE
    }

    public static class CraterPipeline {
        private final Vec3 startPos;
        private BlockPos centerPos;
        private final double meteorRadius, craterRadius, craterDepth, ejectaRadius, thermalRadius, treeImpactRadius;
        private Vec3 direction, horizontalDirection, sideDirection;

        private PipelineStage stage = PipelineStage.PREPARE;
        private final LongSet affectedColumns = new LongOpenHashSet();
        private final List<BlockPos> placedBlocks = new ArrayList<>();
        private List<ExcavatedBlock> excavated = new ArrayList<>();
        private long[] settleArray;
        private int settleCursor = 0;

        public CraterPipeline(Vec3 startPos, BlockPos centerPos, double meteorRadius) {
            this.startPos = startPos;
            this.centerPos = centerPos;
            this.meteorRadius = meteorRadius;
            this.craterRadius = meteorRadius * 2.7 + 2.0;
            this.craterDepth = meteorRadius * 1.15 + 1.5;
            this.ejectaRadius = craterRadius * 3.25;
            this.thermalRadius = craterRadius * 3.5;
            this.treeImpactRadius = Math.max(48.0, thermalRadius * 2.0);
        }

        public boolean step(ServerLevel level) {
            switch (stage) {
                case PREPARE -> {
                    int groundY = findMineralGroundY(level, centerPos.getX(), centerPos.getZ());
                    centerPos = new BlockPos(centerPos.getX(), groundY, centerPos.getZ());

                    if (isWaterHit(level, centerPos)) {
                        handleWaterImpact(level, centerPos, meteorRadius);
                        stage = PipelineStage.DONE;
                        return true;
                    }

                    Vec3 impact = Vec3.atCenterOf(centerPos);
                    direction = impact.subtract(startPos).normalize();
                    horizontalDirection = new Vec3(direction.x, 0.0, direction.z);
                    horizontalDirection = horizontalDirection.lengthSqr() < 0.001 ? new Vec3(1, 0, 0) : horizontalDirection.normalize();
                    sideDirection = new Vec3(-horizontalDirection.z, 0, horizontalDirection.x);

                    stage = PipelineStage.TREES;
                }
                case TREES -> {
                    TreeImpactHandler.applyImpact(level, centerPos, horizontalDirection, meteorRadius, craterRadius, thermalRadius, treeImpactRadius, placedBlocks);
                    for (BlockPos p : placedBlocks) affectedColumns.add(ChunkPos.asLong(p.getX(), p.getZ()));
                    stage = PipelineStage.EXCAVATE;
                }
                case EXCAVATE -> {
                    excavated = excavateCrater(level, centerPos, horizontalDirection, sideDirection, craterRadius, craterDepth, affectedColumns);
                    stage = PipelineStage.EJECTA_AND_DETAILS;
                }
                case EJECTA_AND_DETAILS -> {
                    simulateEjecta(level, centerPos, direction, horizontalDirection, sideDirection, craterRadius, ejectaRadius, excavated, affectedColumns);
                    createCraterRim(level, centerPos, craterRadius, meteorRadius, affectedColumns);
                    createThermalZone(level, centerPos, craterRadius, thermalRadius, affectedColumns);
                    createMeteorFragments(level, centerPos, craterRadius, meteorRadius, affectedColumns);
                    stage = PipelineStage.CAVE_COLLAPSE;
                }
                case CAVE_COLLAPSE -> {
                    collapseSubsurfaceCaves(level, centerPos, craterRadius, craterDepth);
                    settleArray = affectedColumns.toLongArray();
                    settleCursor = 0;
                    stage = PipelineStage.SETTLE;
                }
                case SETTLE -> {
                    int limit = Math.min(settleCursor + 40, settleArray.length);
                    for (int i = settleCursor; i < limit; i++) settleColumn(level, settleArray[i]);
                    settleCursor = limit;
                    if (settleCursor >= settleArray.length) stage = PipelineStage.FINALIZE;
                }
                case FINALIZE -> {
                    createMoltenCore(level, centerPos, meteorRadius);
                    level.playSound(null, centerPos, SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT,
                            Math.min(12.0F, 4.0F + (float) meteorRadius * 1.5F), 0.65F + level.random.nextFloat() * 0.2F);

                    LongSet touchedChunks = new LongOpenHashSet();
                    for (long col : affectedColumns) touchedChunks.add(ChunkPos.asLong(ChunkPos.getX(col) >> 4, ChunkPos.getZ(col) >> 4));
                    for (long cp : touchedChunks) {
                        LevelChunk c = level.getChunkSource().getChunk(ChunkPos.getX(cp), ChunkPos.getZ(cp), false);
                        if (c != null) c.setUnsaved(true);
                    }
                    stage = PipelineStage.DONE;
                    return true;
                }
                case DONE -> {
                    return true;
                }
            }
            return false;
        }
    }

    public static int findMineralGroundY(ServerLevel level, int x, int z) {
        int startY = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z)).getY();
        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos(x, startY, z);
        for (int y = startY; y >= level.getMinBuildHeight(); y--) {
            mut.setY(y);
            if (MaterialPhysics.isMineralGround(level.getBlockState(mut))) return y;
        }
        return level.getMinBuildHeight() + 1;
    }

    private static void settleColumn(ServerLevel level, long col) {
        int x = ChunkPos.getX(col), z = ChunkPos.getZ(col);
        int mineralGround = findMineralGroundY(level, x, z);
        int surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z)).getY();
        if (surface <= mineralGround) return;

        for (int y = mineralGround + 1; y <= surface; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;

            BlockPos target = findDropTarget(level, pos, 384);
            if (!target.equals(pos)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(target, state, 3);
            }
        }
    }

    private static List<ExcavatedBlock> excavateCrater(
            ServerLevel level, BlockPos centerPos, Vec3 horizontalDir, Vec3 sideDir,
            double craterRadius, double craterDepth, LongSet affectedColumns
    ) {
        var random = level.random;
        List<ExcavatedBlock> excavated = new ArrayList<>();

        int minX = (int) Math.floor(centerPos.getX() - craterRadius - 2);
        int maxX = (int) Math.ceil(centerPos.getX() + craterRadius + 2);
        int minZ = (int) Math.floor(centerPos.getZ() - craterRadius - 2);
        int maxZ = (int) Math.ceil(centerPos.getZ() + craterRadius + 2);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double dx = x + 0.5 - centerPos.getX(), dz = z + 0.5 - centerPos.getZ();
                Vec3 horizontal = new Vec3(dx, 0, dz);
                if (horizontal.length() > craterRadius + 2.0) continue;

                double forward = horizontal.dot(horizontalDir);
                double side = horizontal.dot(sideDir);
                double angleStretch = 1.0 + Math.max(0.0, forward / craterRadius) * 0.35;
                double effectiveDistance = Math.sqrt(Math.pow(forward / angleStretch, 2) + side * side);
                double localRadius = craterRadius * (0.88 + noise01(x * 2, z * 2) * 0.24);
                if (effectiveDistance > localRadius) continue;

                double normalized = Math.min(1.0, effectiveDistance / localRadius);
                double localDepth = craterDepth * Math.pow(1.0 - normalized * normalized, 0.72) * (0.90 + noise01(x * 7, z * 11) * 0.20);

                int colGround = findMineralGroundY(level, x, z);
                int surfaceY = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z)).getY();
                int bedrockFloor = findBedrockFloor(level, x, z, colGround);
                int floorY = Math.max(bedrockFloor, (int) Math.floor(colGround - localDepth));

                // Von der realen Oberfläche abwärts aushöhlen, damit geköpfte Stämme über dem Trichter entfernt werden.
                for (int y = surfaceY; y >= floorY; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || !MaterialPhysics.canMeteorDestroy(level, pos)) continue;

                    if (y <= colGround && MaterialPhysics.isMineralGround(state)) {
                        double depthRatio = (double) (colGround - y) / Math.max(1, colGround - floorY);
                        if (random.nextDouble() < (0.25 + depthRatio * 0.65)) {
                            excavated.add(new ExcavatedBlock(state, x, y, z, depthRatio));
                        }
                    }
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
                affectedColumns.add(ChunkPos.asLong(x, z));
            }
        }
        return excavated;
    }

	private static void simulateEjecta(ServerLevel level, BlockPos centerPos, Vec3 impactDir, Vec3 horizDir, Vec3 sideDir, double craterRadius, double ejectaRadius, List<ExcavatedBlock> excavated, LongSet affectedColumns) {
        var random = level.random;

        for (ExcavatedBlock block : excavated) {
            MaterialPhysics mat = MaterialPhysics.analyze(level, new BlockPos(block.x, block.y, block.z), block.state);
            double energy = (0.35 + block.depthRatio * 0.90) * (0.45 + mat.mobility * 0.95) * (0.75 + mat.fragmentation * 0.55) * (1.0 - mat.cohesion * 0.30) * (0.75 + random.nextDouble() * 0.65);
            double distance = craterRadius + Math.pow(random.nextDouble(), 1.65) * (ejectaRadius - craterRadius) * energy;

            double angle = random.nextDouble() * Math.PI * 2.0;
            Vec3 radial = horizDir.scale(Math.cos(angle)).add(sideDir.scale(Math.sin(angle)));
            Vec3 ejectDir = horizDir.scale(1.0 - mat.lateralSpread * 0.35).add(radial.scale(0.65 + mat.lateralSpread * (random.nextDouble() - 0.5))).add(impactDir.scale(0.35)).normalize();

            int targetX = (int) Math.round(centerPos.getX() + ejectDir.x * distance);
            int targetZ = (int) Math.round(centerPos.getZ() + ejectDir.z * distance);

            // Ejecta landet AUF dem Boden (+1) und stapelt sich bei Mehrfachtreffern, statt die Grasnarbe planar zu ersetzen.
            int groundY = findMineralGroundY(level, targetX, targetZ);
            BlockPos landing = new BlockPos(targetX, groundY + 1, targetZ);
            while (landing.getY() < level.getMaxBuildHeight() && !level.getBlockState(landing).isAir() && !MaterialPhysics.isFoliage(level.getBlockState(landing))) {
                landing = landing.above();
            }
            if (!MaterialPhysics.canMeteorDestroy(level, landing)) continue;

            double deposition = 1.0 - Math.min(1.0, distance / ejectaRadius);
            if (random.nextDouble() > (0.08 + deposition * 0.82) * (0.75 + mat.fragmentation * 0.35)) continue;

            BlockState stateToPlace = block.state;
            if (mat.fragmentation > 0.72 && random.nextDouble() < 0.18) {
                Block outer = MeteorConfig.getRandomBlock(MeteorConfig.OUTER_SHELL, random);
                if (outer != null) stateToPlace = outer.defaultBlockState();
            }

            level.setBlock(landing, stateToPlace, 2);
            affectedColumns.add(ChunkPos.asLong(targetX, targetZ));
        }
    }

    private static void createCraterRim(ServerLevel level, BlockPos centerPos, double craterRadius, double meteorRadius, LongSet affectedColumns) {
        var random = level.random;
        int rimRange = (int) Math.ceil(craterRadius * 1.30);

        for (int x = -rimRange; x <= rimRange; x++) {
            for (int z = -rimRange; z <= rimRange; z++) {
                double distance = Math.sqrt(x * x + z * z);
                // Dichter Wallring um die Bruchkante statt löchrigem Zufallsmuster
                if (distance < craterRadius * 0.85 || distance > craterRadius * 1.25) continue;

                int cx = centerPos.getX() + x, cz = centerPos.getZ() + z;
                int groundY = findMineralGroundY(level, cx, cz);

                // Parabolischer Wallkamm mit Peak auf der Kraterkante
                double rimFactor = 1.0 - Math.abs(distance - craterRadius) / (craterRadius * 0.25);
                int height = Math.max(1, (int) Math.round(rimFactor * Math.max(1.8, meteorRadius * 0.65) * (0.75 + noise01(cx, cz) * 0.5)));

                for (int h = 1; h <= height; h++) {
                    BlockPos pos = new BlockPos(cx, groundY + h, cz);
                    BlockState cur = level.getBlockState(pos);
                    // Überschreibt Bodenvegetation (Gras/Blumen/Schnee) kompromisslos mit Auswurfmaterial
                    if ((!cur.isAir() && !MaterialPhysics.isFoliage(cur)) || !MaterialPhysics.canMeteorDestroy(level, pos)) continue;

                    double roll = random.nextDouble();
                    BlockState mat = roll < 0.45 ? MeteorConfig.getRandomBlock(MeteorConfig.OUTER_SHELL, random).defaultBlockState()
                                   : roll < 0.75 ? Blocks.STONE.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();

                    level.setBlock(pos, mat, 2);
                    affectedColumns.add(ChunkPos.asLong(cx, cz));
                }
            }
        }
    }

    private static void createThermalZone(ServerLevel level, BlockPos centerPos, double craterRadius, double thermalRadius, LongSet affectedColumns) {
        var random = level.random;
        int range = (int) Math.ceil(thermalRadius);

        for (int x = -range; x <= range; x++) {
            for (int z = -range; z <= range; z++) {
                double distance = Math.sqrt(x * x + z * z);
                if (distance <= craterRadius || distance > thermalRadius) continue;

                double probability = Math.pow(1.0 - (distance - craterRadius) / (thermalRadius - craterRadius), 2.4);
                if (random.nextDouble() > probability) continue;

                int px = centerPos.getX() + x, pz = centerPos.getZ() + z;
                int groundY = findMineralGroundY(level, px, pz);
                BlockPos surface = new BlockPos(px, groundY, pz);

                if (!MaterialPhysics.canMeteorDestroy(level, surface)) continue;

                BlockState state = level.getBlockState(surface);
                if (state.is(Blocks.GRASS_BLOCK)) {
                    if (random.nextDouble() < 0.65) level.setBlock(surface, Blocks.COARSE_DIRT.defaultBlockState(), 2);
                    
                    BlockPos above = surface.above();
                    if (MaterialPhysics.isFoliage(level.getBlockState(above)) || level.isEmptyBlock(above)) {
                        BlockState topState = (random.nextDouble() < probability * 0.35) ? Blocks.FIRE.defaultBlockState() : Blocks.AIR.defaultBlockState();
                        level.setBlock(above, topState, 2);
                    }
                    affectedColumns.add(ChunkPos.asLong(px, pz));
                }
            }
        }
    }

    private static void createMeteorFragments(ServerLevel level, BlockPos centerPos, double craterRadius, double meteorRadius, LongSet affectedColumns) {
        var random = level.random;
        int fragmentCount = 2 + random.nextInt(Math.max(2, (int) (meteorRadius * 2.5)));

        for (int i = 0; i < fragmentCount; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = 1.5 + random.nextDouble() * (craterRadius * 1.4 - 1.5);

            int x = centerPos.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = centerPos.getZ() + (int) Math.round(Math.sin(angle) * distance);
            int y = findMineralGroundY(level, x, z) + 1;

            BlockPos fragmentPos = new BlockPos(x, y, z);
            if (!level.getBlockState(fragmentPos).isAir() || !MaterialPhysics.canMeteorDestroy(level, fragmentPos)) continue;

            Block fragment = MeteorConfig.getRandomBlock(MeteorConfig.CORE_BLOCKS, random);
            if (fragment != null) {
                level.setBlock(fragmentPos, fragment.defaultBlockState(), 2);
                affectedColumns.add(ChunkPos.asLong(x, z));
            }
        }
    }

    private static void collapseSubsurfaceCaves(ServerLevel level, BlockPos centerPos, double craterRadius, double craterDepth) {
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
                if (Math.abs(x - centerPos.getX()) <= 1 && Math.abs(z - centerPos.getZ()) <= 1) continue;

                double dx = x + 0.5 - centerPos.getX(), dz = z + 0.5 - centerPos.getZ();
                double distSq = dx * dx + dz * dz;
                if (distSq > radiusSq || distSq > radiusSq * (0.82 + noise01(x * 5, z * 5) * 0.32)) continue;

                double shockEnergy = Math.pow(1.0 - (Math.sqrt(distSq) / collapseRadius), 1.3) * (0.85 + noise01(x * 9, z * 9) * 0.30);
                if (shockEnergy < 0.12) continue;

                int colGround = findMineralGroundY(level, x, z);

                for (int y = startY; y >= bottomY; y--) {
                    BlockPos ceilingPos = new BlockPos(x, y, z);
                    BlockState ceilingState = level.getBlockState(ceilingPos);
                    if (ceilingState.isAir() || !MaterialPhysics.canMeteorDestroy(level, ceilingPos) || !level.isEmptyBlock(ceilingPos.below())) continue;

                    int overburden = Math.max(1, colGround - y);
                    double hardness = Math.min(1.0, ceilingState.getDestroySpeed(level, ceilingPos) / 3.0);
                    if (random.nextDouble() > shockEnergy * (1.15 - Math.min(1.0, overburden / 22.0) * 0.75) * (1.10 - hardness * 0.35)) continue;

                    int rupture = Math.max(1, (int) Math.round(3.0 * shockEnergy));
                    for (int r = 0; r < rupture; r++) {
                        BlockPos targetPos = ceilingPos.above(r);
                        BlockState toDrop = level.getBlockState(targetPos);
                        if (toDrop.isAir() || !MaterialPhysics.canMeteorDestroy(level, targetPos)) break;

                        BlockPos floorTarget = findDropTarget(level, targetPos, 25);
                        if (floorTarget.equals(targetPos)) continue;

                        level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), 2);
                        BlockState rubble = (toDrop.is(Blocks.STONE) || toDrop.is(Blocks.DEEPSLATE))
                                ? (random.nextBoolean() ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState())
                                : Blocks.COARSE_DIRT.defaultBlockState();
                        level.setBlock(floorTarget, rubble, 2);
                    }
                    y -= rupture;
                }
            }
        }
    }

    private static void createMoltenCore(ServerLevel level, BlockPos centerPos, double meteorRadius) {
        var random = level.random;
        boolean shouldSpawnRare = random.nextInt(Math.max(1, MeteorConfig.rareBlockInterval)) == 0;
        int radius = Math.max(1, (int) Math.round(meteorRadius * 0.9));

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (Math.sqrt(x * x + z * z) > radius || (x == 0 && z == 0)) continue;

                int cx = centerPos.getX() + x, cz = centerPos.getZ() + z;
                int surfaceY = findMineralGroundY(level, cx, cz);
                BlockPos pos = new BlockPos(cx, surfaceY, cz);

                if (!MaterialPhysics.canMeteorDestroy(level, pos)) continue;

                BlockState mat = (random.nextDouble() < 0.35)
                        ? MeteorConfig.getRandomBlock(MeteorConfig.CORE_BLOCKS, random).defaultBlockState()
                        : Blocks.MAGMA_BLOCK.defaultBlockState();
                level.setBlock(pos, mat, 3);
            }
        }

        int centerY = findMineralGroundY(level, centerPos.getX(), centerPos.getZ());
        BlockPos coreFloorPos = new BlockPos(centerPos.getX(), centerY, centerPos.getZ());
        level.setBlock(coreFloorPos.above(), Blocks.AIR.defaultBlockState(), 3);

        if (shouldSpawnRare) {
            MeteorConfig.spawnRareCoreReward(level, coreFloorPos, random);
        } else {
            level.setBlock(coreFloorPos, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
        }
    }

    private static BlockPos findDropTarget(ServerLevel level, BlockPos startPos, int maxDrop) {
        BlockPos current = startPos;
        for (int dropped = 0; dropped <= maxDrop; dropped++) {
            if (current.getY() <= level.getMinBuildHeight() + 1) return current;

            BlockPos below = current.below();
            BlockState belowState = level.getBlockState(below);
            if (belowState.isAir()) {
                current = below;
                continue;
            }

            MaterialPhysics support = MaterialPhysics.analyze(level, below, belowState);
            if (support.supportStrength >= 0.99) return current;

            if (MaterialPhysics.canMeteorDestroy(level, below)) level.setBlock(below, Blocks.AIR.defaultBlockState(), 2);
            current = below;
        }
        return current;
    }

    private static int findBedrockFloor(ServerLevel level, int x, int z, int surfaceY) {
        for (int y = surfaceY; y >= level.getMinBuildHeight(); y--) {
            if (level.getBlockState(new BlockPos(x, y, z)).is(Blocks.BEDROCK)) return y + 1;
        }
        return level.getMinBuildHeight() + 1;
    }

    private static double noise01(int x, int z) {
        int n = (x + z * 57) << 13 ^ (x + z * 57);
        return ((1.0 - ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / 1073741824.0) + 1.0) * 0.5;
    }

    private static class ExcavatedBlock {
        final BlockState state;
        final int x, y, z;
        final double depthRatio;

        ExcavatedBlock(BlockState state, int x, int y, int z, double depthRatio) {
            this.state = state;
            this.x = x;
            this.y = y;
            this.z = z;
            this.depthRatio = depthRatio;
        }
    }

    private static boolean isWaterHit(ServerLevel level, BlockPos centerPos) {
        int surfaceY = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, centerPos).getY();
        BlockPos topPos = new BlockPos(centerPos.getX(), surfaceY, centerPos.getZ());
        return level.getFluidState(topPos).is(FluidTags.WATER)
                || level.getFluidState(topPos.below()).is(FluidTags.WATER)
                || level.getFluidState(centerPos).is(FluidTags.WATER)
                || level.getFluidState(centerPos.above()).is(FluidTags.WATER);
    }

    private static void handleWaterImpact(ServerLevel level, BlockPos surfacePos, double meteorRadius) {
        var random = level.random;
        BlockPos seabedPos = surfacePos;
        while (seabedPos.getY() > level.getMinBuildHeight() + 1
                && (level.getFluidState(seabedPos).is(FluidTags.WATER) || level.getBlockState(seabedPos).isAir())) {
            seabedPos = seabedPos.below();
        }

        int r = Math.max(1, (int) Math.round(meteorRadius));
        boolean shouldSpawnRare = random.nextInt(Math.max(1, MeteorConfig.rareBlockInterval)) == 0;

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    double dist = Math.sqrt(x * x + y * y + z * z);
                    if (dist > r * (0.85 + noise01((seabedPos.getX() + x) * 4, (seabedPos.getZ() + z) * 4) * 0.30)) continue;

                    BlockPos target = seabedPos.offset(x, y + (r / 2), z);
                    if (!MaterialPhysics.canMeteorDestroy(level, target)) continue;

                    BlockState state = (dist < r * 0.5)
                            ? (random.nextDouble() < 0.35 ? MeteorConfig.getRandomBlock(MeteorConfig.CORE_BLOCKS, random).defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState())
                            : (MeteorConfig.getRandomBlock(MeteorConfig.OUTER_SHELL, random) != null ? MeteorConfig.getRandomBlock(MeteorConfig.OUTER_SHELL, random).defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState());
                    level.setBlock(target, state, 3);
                }
            }
        }

        BlockPos top = seabedPos.offset(0, r + (r / 2), 0);
        while (top.getY() > seabedPos.getY() && (level.getBlockState(top).isAir() || level.getFluidState(top).is(FluidTags.WATER))) {
            top = top.below();
        }

        if (shouldSpawnRare) MeteorConfig.spawnRareCoreReward(level, top, random);

        level.playSound(null, surfacePos, SoundEvents.GENERIC_SPLASH, SoundSource.AMBIENT, 6.0F, 0.8F + random.nextFloat() * 0.3F);
        level.playSound(null, surfacePos, SoundEvents.FIRE_EXTINGUISH, SoundSource.AMBIENT, 5.0F, 0.7F + random.nextFloat() * 0.2F);
        level.playSound(null, seabedPos, SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT, Math.min(8.0F, 3.0F + (float) meteorRadius), 0.50F + random.nextFloat() * 0.15F);
    }
}