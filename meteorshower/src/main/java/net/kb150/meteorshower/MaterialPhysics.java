package net.kb150.meteorshower;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Beschreibt die physikalischen Eigenschaften eines Blocks
 * für die Ejecta-Simulation und das Deformationsverhalten
 * bei einem Meteoriteneinschlag.
 */
public final class MaterialPhysics {

    /**
     * 0 = extrem leicht, 1 = extrem schwer
     */
    public final double mass;

    /**
     * 0 = zerfällt sehr leicht, 1 = sehr kohärentes Material
     */
    public final double cohesion;

    /**
     * 0 = wenig Fragmentierung, 1 = sehr starke Fragmentierung
     */
    public final double fragmentation;

    /**
     * 0 = bleibt fast vollständig am Krater, 1 = kann weit ausgeworfen werden
     */
    public final double mobility;

    /**
     * 0 = kaum seitliche Streuung, 1 = starke seitliche Streuung
     */
    public final double lateralSpread;

    /**
     * Normierte Härte (0.0 bis 1.0).
     */
    public final double hardness;

    /**
     * 1.0 = vollwertige, tragfähige Blockfläche, 0.0 = keine
     * belastbare Oberfläche für das Meteor-Settlement.
     *
     * Die Information wird aus der tatsächlichen Minecraft-Kollisionsform
     * des BlockStates abgeleitet und kennt deshalb keine einzelnen
     * Blocktypen wie Schnee, Blätter oder Mod-Blöcke.
     */
    public final double supportStrength;

    /** True, wenn der Block überhaupt eine feste Kollisionsstruktur besitzt. */
    public final boolean hasSolidCollision;

    /** True, wenn die Kollisionsform den kompletten Block ausfüllt. */
    public final boolean fullCollision;

    public final boolean pickaxe;
    public final boolean shovel;
    public final boolean axe;
    public final boolean hoe;

    private MaterialPhysics(
            double mass,
            double cohesion,
            double fragmentation,
            double mobility,
            double lateralSpread,
            double hardness,
            double supportStrength,
            boolean hasSolidCollision,
            boolean fullCollision,
            boolean pickaxe,
            boolean shovel,
            boolean axe,
            boolean hoe
    ) {
        this.mass = mass;
        this.cohesion = cohesion;
        this.fragmentation = fragmentation;
        this.mobility = mobility;
        this.lateralSpread = lateralSpread;
        this.hardness = hardness;
        this.supportStrength = supportStrength;
        this.hasSolidCollision = hasSolidCollision;
        this.fullCollision = fullCollision;
        this.pickaxe = pickaxe;
        this.shovel = shovel;
        this.axe = axe;
        this.hoe = hoe;
    }

    /**
     * Analysiert einen Block anhand seiner Spieleigenschaften.
     */
    public static MaterialPhysics analyze(
            ServerLevel level,
            BlockPos pos,
            BlockState state
    ) {
        double rawHardness = state.getDestroySpeed(level, pos);

        boolean hasSolidCollision =
                state.getFluidState().isEmpty() && !state.isAir() && state.blocksMotion();

        boolean fullCollision =
                hasSolidCollision && state.isCollisionShapeFullBlock(level, pos);

        double supportStrength = fullCollision ? 1.0 : 0.0;

        if (rawHardness < 0.0) {
            return new MaterialPhysics(
                    1.0,
                    1.0,
                    0.0,
                    0.0,
                    0.0,
                    1.0,
                    supportStrength,
                    hasSolidCollision,
                    fullCollision,
                    false,
                    false,
                    false,
                    false
            );
        }

        double hardness = normalizeHardness(rawHardness);

        boolean pickaxe = state.is(BlockTags.MINEABLE_WITH_PICKAXE);
        boolean shovel = state.is(BlockTags.MINEABLE_WITH_SHOVEL);
        boolean axe = state.is(BlockTags.MINEABLE_WITH_AXE);
        boolean hoe = state.is(BlockTags.MINEABLE_WITH_HOE);

        double cohesion = 0.25 + hardness * 0.65;
        double fragmentation = 0.70 - hardness * 0.45;
        double mobility = 0.65 - hardness * 0.25;
        double lateralSpread = 0.60;

        if (shovel) {
            cohesion -= 0.18;
            fragmentation += 0.20;
            mobility += 0.18;
            lateralSpread += 0.18;
        }

        if (pickaxe) {
            cohesion += 0.18;
            fragmentation -= 0.15;
            mobility -= 0.12;
            lateralSpread -= 0.10;
        }

        if (axe) {
            cohesion -= 0.08;
            fragmentation += 0.08;
            mobility += 0.05;
            lateralSpread += 0.05;
        }

        if (hoe) {
            cohesion -= 0.12;
            fragmentation += 0.15;
            mobility += 0.12;
            lateralSpread += 0.12;
        }

        cohesion = clamp01(cohesion);
        fragmentation = clamp01(fragmentation);
        mobility = clamp01(mobility);
        lateralSpread = clamp01(lateralSpread);

        double mass = 0.25
                + hardness * 0.55
                + (pickaxe ? 0.12 : 0.0)
                - (shovel ? 0.10 : 0.0);

        mass = clamp01(mass);

        return new MaterialPhysics(
                mass,
                cohesion,
                fragmentation,
                mobility,
                lateralSpread,
                hardness,
                supportStrength,
                hasSolidCollision,
                fullCollision,
                pickaxe,
                shovel,
                axe,
                hoe
        );
    }

    private static double normalizeHardness(double hardness) {
        if (hardness <= 0.0) {
            return 0.0;
        }
        return clamp01(Math.log1p(hardness) / Math.log1p(50.0));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}