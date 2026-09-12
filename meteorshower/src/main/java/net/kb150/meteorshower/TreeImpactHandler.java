package net.kb150.meteorshower;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Verarbeitet Vegetation und Bäume im Schockwellenbereich eines Meteors.
 *
 * Die Klasse arbeitet bewusst vor der Terrain-Zerstörung:
 * - Bäume werden zunächst als zusammenhängende Strukturen erkannt.
 * - Nahe Bäume können vollständig umgelegt werden.
 * - Weiter außen werden sie primär entlaubt.
 * - Gefällte Stämme werden in eine horizontale Fallrichtung gelegt.
 *
 * Alle tatsächlich neu gesetzten Stammblöcke werden in placedBlocks aufgenommen,
 * damit das finale Settlement sie nach allen anderen Impact-Schritten ebenfalls prüfen kann.
 */
public final class TreeImpactHandler {

    private static final int MAX_TREE_LOGS = 512;
    private static final int MAX_TREE_HEIGHT_SCAN = 40;
    private static final int LEAF_SEARCH_MARGIN = 5;

    private TreeImpactHandler() {
    }

    public static void applyImpact(
            ServerLevel level,
            BlockPos centerPos,
            Vec3 horizontalDirection,
            double meteorRadius,
            double craterRadius,
            double treeImpactRadius,
            List<BlockPos> placedBlocks
    ) {
        int scanRadius = (int) Math.ceil(treeImpactRadius);
        Set<BlockPos> visitedLogs = new HashSet<>();

        for (int x = centerPos.getX() - scanRadius; x <= centerPos.getX() + scanRadius; x++) {
            for (int z = centerPos.getZ() - scanRadius; z <= centerPos.getZ() + scanRadius; z++) {
                double dx = x + 0.5 - centerPos.getX();
                double dz = z + 0.5 - centerPos.getZ();

                if (dx * dx + dz * dz > treeImpactRadius * treeImpactRadius) {
                    continue;
                }

                int surfaceY = level.getHeightmapPos(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        new BlockPos(x, 0, z)
                ).getY();

                int minY = Math.max(level.getMinBuildHeight() + 1, surfaceY - 3);
                int maxY = Math.min(level.getMaxBuildHeight() - 1, surfaceY + MAX_TREE_HEIGHT_SCAN);

                for (int y = minY; y <= maxY; y++) {
                    BlockPos candidate = new BlockPos(x, y, z);

                    if (visitedLogs.contains(candidate) || !isTrunk(candidateState(level, candidate))) {
                        continue;
                    }

                    TreeStructure tree = collectTree(level, candidate, visitedLogs);
                    if (tree == null) {
                        continue;
                    }

                    processTree(
                            level,
                            centerPos,
                            horizontalDirection,
                            meteorRadius,
                            craterRadius,
                            treeImpactRadius,
                            tree,
                            placedBlocks
                    );
                }
            }
        }
    }

    private static TreeStructure collectTree(
            ServerLevel level,
            BlockPos start,
            Set<BlockPos> globalVisited
    ) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> treeLogs = new HashSet<>();
        queue.add(start);

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        while (!queue.isEmpty() && treeLogs.size() < MAX_TREE_LOGS) {
            BlockPos pos = queue.removeFirst();

            if (!treeLogs.add(pos)) {
                continue;
            }

            globalVisited.add(pos);
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());

            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!treeLogs.contains(next)
                        && !globalVisited.contains(next)
                        && isTrunk(candidateState(level, next))) {
                    queue.addLast(next);
                }
            }
        }

        if (treeLogs.size() < 3 || maxY - minY < 3) {
            return null;
        }

        BlockPos base = findBase(level, treeLogs, minY);
        if (base == null) {
            return null;
        }

        int leafMinX = minX - LEAF_SEARCH_MARGIN;
        int leafMaxX = maxX + LEAF_SEARCH_MARGIN;
        int leafMinY = Math.max(level.getMinBuildHeight() + 1, minY - 2);
        int leafMaxY = Math.min(level.getMaxBuildHeight() - 1, maxY + LEAF_SEARCH_MARGIN);
        int leafMinZ = minZ - LEAF_SEARCH_MARGIN;
        int leafMaxZ = maxZ + LEAF_SEARCH_MARGIN;

        List<BlockPos> leaves = new ArrayList<>();
        for (int x = leafMinX; x <= leafMaxX; x++) {
            for (int z = leafMinZ; z <= leafMaxZ; z++) {
                for (int y = leafMinY; y <= leafMaxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (treeLogs.contains(pos)) {
                        continue;
                    }
                    if (isLeaf(candidateState(level, pos))) {
                        leaves.add(pos);
                    }
                }
            }
        }

        // Ein echtes Gehölz hat neben dem Stamm eine erkennbare Blattkrone.
        // Diese Bedingung schützt vor normalen Holzgebäuden / Zäunen / Pfosten.
        if (leaves.size() < 8) {
            return null;
        }

        Vec3 centerOfMass = calculateCenterOfMass(treeLogs);

        return new TreeStructure(
                new ArrayList<>(treeLogs),
                leaves,
                base,
                centerOfMass,
                minY,
                maxY,
                minX,
                maxX,
                minZ,
                maxZ
        );
    }

    private static void processTree(
            ServerLevel level,
            BlockPos impactPos,
            Vec3 horizontalDirection,
            double meteorRadius,
            double craterRadius,
            double treeImpactRadius,
            TreeStructure tree,
            List<BlockPos> placedBlocks
    ) {
        double dx = tree.base.getX() + 0.5 - impactPos.getX();
        double dz = tree.base.getZ() + 0.5 - impactPos.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        if (distance > treeImpactRadius) {
            return;
        }

        double blast = 1.0 - Math.min(1.0, distance / treeImpactRadius);
        double size = tree.logs.size();

        // Größere Bäume haben mehr Trägheit und widerstehen dem Umknicken besser.
        double sizeResistance = Math.min(0.48, 0.18 + (size / 180.0) * 0.30);

        // Im eigentlichen Kraterbereich wird der Baum schlicht zerstört.
        if (distance <= craterRadius + 2.0) {
            destroyTree(level, tree);
            return;
        }

        // Fallwahrscheinlichkeit steigt stark mit der Schockwelle, aber große Bäume
        // widerstehen ihr besser. Ein kleiner Zufallsanteil verhindert identische Muster.
        double fallChance = Math.pow(blast, 1.25)
                * (0.92 - sizeResistance)
                * (0.95 + Math.min(0.25, meteorRadius / 20.0));
        fallChance = clamp01(fallChance);

        if (level.random.nextDouble() < fallChance) {
            fellTree(level, impactPos, horizontalDirection, tree, placedBlocks);
        } else {
            double defoliationChance = clamp01(0.45 + blast * 0.50);
            defoliate(level, tree.leaves, defoliationChance);
        }
    }

    private static void fellTree(
            ServerLevel level,
            BlockPos impactPos,
            Vec3 horizontalDirection,
            TreeStructure tree,
            List<BlockPos> placedBlocks
    ) {
        Vec3 fallDirection = horizontalDirection.normalize();
        if (fallDirection.lengthSqr() < 0.001) {
            fallDirection = new Vec3(1, 0, 0);
        }

        // Kleine seitliche Streuung, damit ein ganzer Wald nicht mathematisch parallel fällt.
        double sideAmount = level.random.nextDouble() * 0.22 - 0.11;
        Vec3 side = new Vec3(-fallDirection.z, 0, fallDirection.x);
        fallDirection = fallDirection
                .add(side.scale(sideAmount))
                .normalize();

        // Die originalen Zustände müssen vor dem Entfernen gesichert werden,
        // sonst wäre beim späteren Umlegen nur noch AIR vorhanden.
        java.util.Map<BlockPos, BlockState> originalLogStates = new java.util.HashMap<>();
        for (BlockPos log : tree.logs) {
            BlockState state = candidateState(level, log);
            if (isTrunk(state)) {
                originalLogStates.put(log, state);
            }
        }

        // Erst alles entfernen, damit die neue, liegende Geometrie nicht mit der alten Krone kollidiert.
        for (BlockPos leaf : tree.leaves) {
            if (isLeaf(candidateState(level, leaf))) {
                level.setBlock(leaf, Blocks.AIR.defaultBlockState(), 3);
            }
        }

        for (BlockPos log : tree.logs) {
            if (isTrunk(candidateState(level, log))) {
                level.setBlock(log, Blocks.AIR.defaultBlockState(), 3);
            }
        }

        List<BlockPos> logs = new ArrayList<>(tree.logs);
        logs.sort(Comparator.comparingInt(BlockPos::getY));

        double baseX = tree.base.getX() + 0.5;
        double baseZ = tree.base.getZ() + 0.5;
        int baseY = tree.base.getY();
        double treeHeight = Math.max(1.0, tree.maxY - tree.minY + 1.0);

        Set<BlockPos> newlyPlaced = new HashSet<>();

        for (BlockPos original : logs) {
            double verticalOffset = original.getY() - baseY;
            double along = verticalOffset * (0.78 + level.random.nextDouble() * 0.16);

            double lateralX = (original.getX() + 0.5 - baseX) * 0.85;
            double lateralZ = (original.getZ() + 0.5 - baseZ) * 0.85;

            int targetX = (int) Math.round(baseX + fallDirection.x * along + lateralX);
            int targetZ = (int) Math.round(baseZ + fallDirection.z * along + lateralZ);

            int supportY = findSolidGroundY(level, targetX, targetZ, Math.max(48, (int) Math.ceil(treeHeight + 8.0)));
            if (supportY == Integer.MIN_VALUE) {
                continue;
            }

            BlockPos target = new BlockPos(targetX, supportY, targetZ);

            // Falls der Sturzweg auf einem festen Hindernis endet, versuchen wir bis zu
            // drei Blöcke entlang der Fallrichtung auszuweichen.
            for (int attempt = 0; attempt < 4 && !canPlaceFallenLog(level, target, newlyPlaced); attempt++) {
                target = target.relative(horizontalDirectionToDirection(fallDirection));
                int retryY = findSolidGroundY(level, target.getX(), target.getZ(), 48);
                if (retryY != Integer.MIN_VALUE) {
                    target = new BlockPos(target.getX(), retryY, target.getZ());
                }
            }

            if (!canPlaceFallenLog(level, target, newlyPlaced)) {
                continue;
            }

            BlockState originalState = originalLogStates.get(original);
            BlockState fallenState = makeHorizontalLogState(originalState, fallDirection);
            if (fallenState == null) {
                continue;
            }

            level.setBlock(target, fallenState, 3);
            newlyPlaced.add(target);
            placedBlocks.add(target);
        }

        // Ein paar Blätter bleiben bei einem kräftigen Umknicken bewusst nicht erhalten:
        // Der Baum ist weitgehend entlaubt, damit keine schwebende Krone zurückbleibt.
        // Das erzeugt gleichzeitig eine saubere Voraussetzung für das finale Settlement.
    }

    private static void destroyTree(ServerLevel level, TreeStructure tree) {
        for (BlockPos leaf : tree.leaves) {
            if (isLeaf(candidateState(level, leaf))) {
                level.setBlock(leaf, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        for (BlockPos log : tree.logs) {
            if (isTrunk(candidateState(level, log))) {
                level.setBlock(log, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private static void defoliate(ServerLevel level, List<BlockPos> leaves, double chance) {
        for (BlockPos leaf : leaves) {
            if (level.random.nextDouble() <= chance && isLeaf(candidateState(level, leaf))) {
                level.setBlock(leaf, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private static BlockPos findBase(
            ServerLevel level,
            Set<BlockPos> logs,
            int minY
    ) {
        BlockPos best = null;
        double bestSupport = -1.0;

        for (BlockPos log : logs) {
            if (log.getY() != minY) {
                continue;
            }

            BlockState below = candidateState(level, log.below());
            double support = below.isAir() ? 0.0 : (below.isFaceSturdy(level, log.below(), Direction.UP) ? 1.0 : 0.5);
            if (support > bestSupport) {
                bestSupport = support;
                best = log;
            }
        }

        return best;
    }

    private static Vec3 calculateCenterOfMass(Set<BlockPos> logs) {
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;

        for (BlockPos pos : logs) {
            x += pos.getX() + 0.5;
            y += pos.getY() + 0.5;
            z += pos.getZ() + 0.5;
        }

        double count = Math.max(1, logs.size());
        return new Vec3(x / count, y / count, z / count);
    }

    private static int findSolidGroundY(ServerLevel level, int x, int z, int depth) {
        int startY = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(x, 0, z)
        ).getY();

        int minY = Math.max(level.getMinBuildHeight() + 1, startY - depth);

        for (int y = startY; y >= minY; y--) {
            BlockPos support = new BlockPos(x, y - 1, z);
            if (isSolidSupport(level, support)) {
                return y;
            }
        }

        return Integer.MIN_VALUE;
    }

    private static boolean isSolidSupport(ServerLevel level, BlockPos pos) {
        BlockState state = candidateState(level, pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (isLeaf(state) || isSoftVegetation(state)) {
            return false;
        }
        return state.isFaceSturdy(level, pos, Direction.UP);
    }

    private static boolean canPlaceFallenLog(
            ServerLevel level,
            BlockPos pos,
            Set<BlockPos> newlyPlaced
    ) {
        if (newlyPlaced.contains(pos)) {
            return false;
        }

        BlockState state = candidateState(level, pos);
        if (state.isAir()) {
            return true;
        }
        if (!state.getFluidState().isEmpty() || isLeaf(state) || isSoftVegetation(state)) {
            return true;
        }
        return false;
    }

    private static BlockState makeHorizontalLogState(BlockState state, Vec3 direction) {
        if (state == null || !state.hasProperty(RotatedPillarBlock.AXIS)) {
            return state;
        }

        Direction.Axis axis = Math.abs(direction.x) >= Math.abs(direction.z)
                ? Direction.Axis.X
                : Direction.Axis.Z;

        return state.setValue(RotatedPillarBlock.AXIS, axis);
    }

    private static Direction horizontalDirectionToDirection(Vec3 direction) {
        if (Math.abs(direction.x) >= Math.abs(direction.z)) {
            return direction.x >= 0.0 ? Direction.EAST : Direction.WEST;
        }
        return direction.z >= 0.0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static boolean isTrunk(BlockState state) {
        return state != null && state.is(BlockTags.LOGS);
    }

    private static boolean isLeaf(BlockState state) {
        return state != null && state.is(BlockTags.LEAVES);
    }

    private static boolean isSoftVegetation(BlockState state) {
        if (state == null) {
            return false;
        }

        return state.is(BlockTags.FLOWERS)
                || state.is(Blocks.GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.VINE)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(BlockTags.SAPLINGS);
    }

    private static BlockState candidateState(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static final class TreeStructure {
        final List<BlockPos> logs;
        final List<BlockPos> leaves;
        final BlockPos base;
        final Vec3 centerOfMass;
        final int minY;
        final int maxY;
        final int minX;
        final int maxX;
        final int minZ;
        final int maxZ;

        private TreeStructure(
                List<BlockPos> logs,
                List<BlockPos> leaves,
                BlockPos base,
                Vec3 centerOfMass,
                int minY,
                int maxY,
                int minX,
                int maxX,
                int minZ,
                int maxZ
        ) {
            this.logs = logs;
            this.leaves = leaves;
            this.base = base;
            this.centerOfMass = centerOfMass;
            this.minY = minY;
            this.maxY = maxY;
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }
    }
}
