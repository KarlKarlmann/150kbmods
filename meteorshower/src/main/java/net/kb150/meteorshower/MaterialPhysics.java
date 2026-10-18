package net.kb150.meteorshower;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class MaterialPhysics {

    public final double mass, cohesion, fragmentation, mobility, lateralSpread, hardness, supportStrength;
    public final boolean hasSolidCollision, fullCollision, pickaxe, shovel, axe, hoe;

    private MaterialPhysics(double mass, double cohesion, double fragmentation, double mobility,
                            double lateralSpread, double hardness, double supportStrength,
                            boolean hasSolidCollision, boolean fullCollision,
                            boolean pickaxe, boolean shovel, boolean axe, boolean hoe) {
        this.mass = mass; this.cohesion = cohesion; this.fragmentation = fragmentation;
        this.mobility = mobility; this.lateralSpread = lateralSpread; this.hardness = hardness;
        this.supportStrength = supportStrength; this.hasSolidCollision = hasSolidCollision;
        this.fullCollision = fullCollision; this.pickaxe = pickaxe; this.shovel = shovel;
        this.axe = axe; this.hoe = hoe;
    }

    public static boolean isAxeMineable(BlockState state) {
        return state != null && !state.isAir() && state.is(BlockTags.MINEABLE_WITH_AXE);
    }

    public static boolean isTrunk(BlockState state) {
        return state != null && !state.isAir() && state.is(BlockTags.LOGS);
    }

    public static boolean isLeaf(BlockState state) {
        return state != null && !state.isAir() && state.is(BlockTags.LEAVES);
    }

    public static boolean isFoliage(BlockState state) {
        if (state == null || state.isAir()) return false;
        return isLeaf(state)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.REPLACEABLE)
                || state.is(Blocks.VINE)
                || state.is(Blocks.GLOW_LICHEN)
                || state.is(Blocks.HANGING_ROOTS)
                || state.is(Blocks.MOSS_CARPET)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN);
    }

    // Holz, Kohleblöcke und Laub als Bodenfundament ausschließen, damit fallende Trümmer nicht im Geäst hängenbleiben.
    public static boolean isMineralGround(BlockState state) {
        if (state == null || state.isAir() || !state.getFluidState().isEmpty()) return false;
        if (isAxeMineable(state) || isFoliage(state) || state.is(Blocks.COAL_BLOCK)) return false;
        return state.blocksMotion();
    }

    public static boolean canMeteorDestroy(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.is(Blocks.BEDROCK)
                && !state.is(Blocks.BARRIER)
                && !state.is(Blocks.END_PORTAL)
                && !state.is(Blocks.END_PORTAL_FRAME)
                && state.getDestroySpeed(level, pos) >= 0.0F;
    }

    public static MaterialPhysics analyze(ServerLevel level, BlockPos pos, BlockState state) {
        double rawHardness = state.getDestroySpeed(level, pos);
        boolean hasSolidCollision = state.getFluidState().isEmpty() && !state.isAir() && state.blocksMotion();
        boolean fullCollision = hasSolidCollision && state.isCollisionShapeFullBlock(level, pos);

        // Blätter brechen unter Aufprallgewicht physikalisch zusammen und bieten 0 Tragkraft für Trümmer.
        double supportStrength = (fullCollision && !isFoliage(state) && rawHardness >= 0.4) ? 1.0 : 0.0;

        if (rawHardness < 0.0) {
            return new MaterialPhysics(1.0, 1.0, 0.0, 0.0, 0.0, 1.0, 1.0, hasSolidCollision, fullCollision, false, false, false, false);
        }

        double hardness = rawHardness <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, Math.log1p(rawHardness) / Math.log1p(50.0)));
        boolean pickaxe = state.is(BlockTags.MINEABLE_WITH_PICKAXE);
        boolean shovel = state.is(BlockTags.MINEABLE_WITH_SHOVEL);
        boolean axe = isAxeMineable(state);
        boolean hoe = state.is(BlockTags.MINEABLE_WITH_HOE);

        double cohesion = Math.max(0.0, Math.min(1.0, 0.25 + hardness * 0.65 + (pickaxe ? 0.18 : 0.0) - (shovel ? 0.18 : 0.0) - (axe ? 0.08 : 0.0) - (hoe ? 0.12 : 0.0)));
        double fragmentation = Math.max(0.0, Math.min(1.0, 0.70 - hardness * 0.45 + (shovel ? 0.20 : 0.0) - (pickaxe ? 0.15 : 0.0) + (axe ? 0.08 : 0.0) + (hoe ? 0.15 : 0.0)));
        double mobility = Math.max(0.0, Math.min(1.0, 0.65 - hardness * 0.25 + (shovel ? 0.18 : 0.0) - (pickaxe ? 0.12 : 0.0) + (axe ? 0.05 : 0.0) + (hoe ? 0.12 : 0.0)));
        double lateralSpread = Math.max(0.0, Math.min(1.0, 0.60 + (shovel ? 0.18 : 0.0) - (pickaxe ? 0.10 : 0.0) + (axe ? 0.05 : 0.0) + (hoe ? 0.12 : 0.0)));
        double mass = Math.max(0.0, Math.min(1.0, 0.25 + hardness * 0.55 + (pickaxe ? 0.12 : 0.0) - (shovel ? 0.10 : 0.0)));

        return new MaterialPhysics(mass, cohesion, fragmentation, mobility, lateralSpread, hardness, supportStrength, hasSolidCollision, fullCollision, pickaxe, shovel, axe, hoe);
    }
}