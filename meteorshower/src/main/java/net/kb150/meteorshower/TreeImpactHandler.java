package net.kb150.meteorshower;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public final class TreeImpactHandler {

    private static final int MAX_TREE_LOGS = 96;

    private TreeImpactHandler() {}

    public static void applyImpact(
            ServerLevel level, BlockPos centerPos, Vec3 horizontalDir,
            double meteorRadius, double craterRadius, double thermalRadius, double treeImpactRadius,
            List<BlockPos> placedBlocks
    ) {
        int scanRadius = (int) Math.ceil(treeImpactRadius);
        Set<BlockPos> visitedTrunks = new HashSet<>();

        for (int x = centerPos.getX() - scanRadius; x <= centerPos.getX() + scanRadius; x++) {
            for (int z = centerPos.getZ() - scanRadius; z <= centerPos.getZ() + scanRadius; z++) {
                double distSq = Math.pow(x + 0.5 - centerPos.getX(), 2) + Math.pow(z + 0.5 - centerPos.getZ(), 2);
                if (distSq > treeImpactRadius * treeImpactRadius) continue;

                double dist = Math.sqrt(distSq);
                int groundY = MeteorImpact.findMineralGroundY(level, x, z);
                BlockPos basePos = new BlockPos(x, groundY + 1, z);

                // Bodengestrüpp im Hitzebereich direkt zu Luft auflösen statt zweiter Schleife im Impact-Code.
                if (dist <= thermalRadius) {
                    BlockState groundState = level.getBlockState(basePos);
                    if (MaterialPhysics.isFoliage(groundState)) {
                        level.setBlock(basePos, Blocks.AIR.defaultBlockState(), 2);
                        placedBlocks.add(basePos);
                    }
                }

                // Bäume wurzeln direkt auf Mineralboden: Prüfung an groundY + 1 spart 40 vertikale Leer-Scans pro Spalte.
                BlockState baseState = level.getBlockState(basePos);
                if (!MaterialPhysics.isTrunk(baseState) || visitedTrunks.contains(basePos)) continue;

                TreeStructure tree = collectTree(level, basePos, visitedTrunks);
                if (tree == null) continue;

                // Kraterzone vernichtet jegliche organische Masse restlos zu Luft.
                if (dist <= craterRadius + 2.0) {
                    clearTreeToAir(level, tree, placedBlocks);
                    continue;
                }

                double blast = 1.0 - (dist / treeImpactRadius);
                boolean falls = level.random.nextDouble() < Math.max(0.15, blast * 1.3);
                boolean isThermal = dist <= thermalRadius;

                // Laub verpufft bei Einschlagseinwirkung immer zu Luft; nur Axt-Holz darf verkohlen.
                stripLeavesToAir(level, tree.leaves, placedBlocks);

                if (falls) {
                    fellTree(level, horizontalDir, tree, placedBlocks, isThermal);
                } else if (isThermal) {
                    for (BlockPos log : tree.logs) {
                        level.setBlock(log, MeteorConfig.getRandomThermalDecay(level.random), 3);
                        placedBlocks.add(log);
                    }
                }
            }
        }
    }

    private static TreeStructure collectTree(ServerLevel level, BlockPos start, Set<BlockPos> globalVisited) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> logs = new HashSet<>();
        queue.add(start);

        while (!queue.isEmpty() && logs.size() < MAX_TREE_LOGS) {
            BlockPos pos = queue.removeFirst();
            if (!logs.add(pos)) continue;

            globalVisited.add(pos);

            // 26er-Nachbarschaft erfasst diagonale Astabzweigungen, damit keine Stümpfe im Himmel schweben bleiben.
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos next = pos.offset(dx, dy, dz);
                        if (!logs.contains(next) && !globalVisited.contains(next) && MaterialPhysics.isTrunk(level.getBlockState(next))) {
                            queue.addLast(next);
                        }
                    }
                }
            }
        }

        if (logs.size() < 2) return null;

        // Radius von 2 Blöcken um jeden Stammteil sammelt das Kronendach vollständig ein, ohne große Boxen abzutasten.
        List<BlockPos> leaves = new ArrayList<>();
        for (BlockPos log : logs) {
            for (int lx = -2; lx <= 2; lx++) {
                for (int ly = -2; ly <= 2; ly++) {
                    for (int lz = -2; lz <= 2; lz++) {
                        BlockPos lp = log.offset(lx, ly, lz);
                        if (!logs.contains(lp) && MaterialPhysics.isLeaf(level.getBlockState(lp))) {
                            leaves.add(lp);
                        }
                    }
                }
            }
        }

        // Reine Holzpfeiler und Holzhütten von Spielern ohne Laubkronen vor Zerstörung schützen.
        if (leaves.isEmpty()) return null;

        return new TreeStructure(new ArrayList<>(logs), leaves, start);
    }

    private static void fellTree(ServerLevel level, Vec3 horizontalDir, TreeStructure tree, List<BlockPos> placedBlocks, boolean charWood) {
        Vec3 dir = horizontalDir.lengthSqr() < 0.001 ? new Vec3(1, 0, 0) : horizontalDir.normalize();
        dir = dir.add(new Vec3(-dir.z, 0, dir.x).scale(level.random.nextDouble() * 0.2 - 0.1)).normalize();

        Map<BlockPos, BlockState> oldStates = new HashMap<>();
        for (BlockPos log : tree.logs) {
            oldStates.put(log, level.getBlockState(log));
            level.setBlock(log, Blocks.AIR.defaultBlockState(), 3);
            placedBlocks.add(log);
        }

        List<BlockPos> sorted = new ArrayList<>(tree.logs);
        sorted.sort(Comparator.comparingInt(BlockPos::getY));

        Direction.Axis axis = Math.abs(dir.x) >= Math.abs(dir.z) ? Direction.Axis.X : Direction.Axis.Z;
        Set<BlockPos> placedThisTree = new HashSet<>();

        for (int i = 0; i < sorted.size(); i++) {
            int step = i + 1;
            int tx = tree.base.getX() + (int) Math.round(dir.x * step);
            int tz = tree.base.getZ() + (int) Math.round(dir.z * step);

            // Stämme landen ausschließlich auf Mineralboden (DIRT/STONE), um Ablage auf Nachbarkronen unmöglich zu machen.
            int groundY = MeteorImpact.findMineralGroundY(level, tx, tz);
            BlockPos target = new BlockPos(tx, groundY + 1, tz);

            if (!placedThisTree.add(target)) continue;

            BlockState cur = level.getBlockState(target);
            if (cur.isAir() || MaterialPhysics.isFoliage(cur)) {
                BlockState finalState;
                if (charWood) {
                    finalState = MeteorConfig.getRandomThermalDecay(level.random);
                } else {
                    BlockState origSt = oldStates.get(sorted.get(i));
                    finalState = (origSt != null && origSt.hasProperty(RotatedPillarBlock.AXIS))
                            ? origSt.setValue(RotatedPillarBlock.AXIS, axis)
                            : Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
                }
                level.setBlock(target, finalState, 3);
                placedBlocks.add(target);
            }
        }
    }

    private static void clearTreeToAir(ServerLevel level, TreeStructure tree, List<BlockPos> placedBlocks) {
        for (BlockPos leaf : tree.leaves) {
            level.setBlock(leaf, Blocks.AIR.defaultBlockState(), 3);
            placedBlocks.add(leaf);
        }
        for (BlockPos log : tree.logs) {
            level.setBlock(log, Blocks.AIR.defaultBlockState(), 3);
            placedBlocks.add(log);
        }
    }

    private static void stripLeavesToAir(ServerLevel level, List<BlockPos> leaves, List<BlockPos> placedBlocks) {
        for (BlockPos leaf : leaves) {
            level.setBlock(leaf, Blocks.AIR.defaultBlockState(), 3);
            placedBlocks.add(leaf);
        }
    }

    private static final class TreeStructure {
        final List<BlockPos> logs, leaves;
        final BlockPos base;
        private TreeStructure(List<BlockPos> logs, List<BlockPos> leaves, BlockPos base) {
            this.logs = logs; this.leaves = leaves; this.base = base;
        }
    }
}