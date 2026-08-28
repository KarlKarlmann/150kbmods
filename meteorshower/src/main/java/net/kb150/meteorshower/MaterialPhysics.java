package net.kb150.meteorshower;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Beschreibt die physikalischen Eigenschaften eines Blocks
 * für die Ejecta-Simulation eines Meteoriteneinschlags.
 *
 * Die Werte sind bewusst kontinuierlich und nicht an konkrete
 * Blocktypen gebunden.
 */
public final class MaterialPhysics {

    /**
     * 0 = extrem leicht
     * 1 = extrem schwer
     */
    public final double mass;

    /**
     * 0 = zerfällt sehr leicht
     * 1 = sehr kohärentes Material
     */
    public final double cohesion;

    /**
     * 0 = wenig Fragmentierung
     * 1 = sehr starke Fragmentierung
     */
    public final double fragmentation;

    /**
     * 0 = bleibt fast vollständig am Krater
     * 1 = kann weit ausgeworfen werden
     */
    public final double mobility;

    /**
     * 0 = kaum seitliche Streuung
     * 1 = starke seitliche Streuung
     */
    public final double lateralSpread;

    /**
     * Normierte Härte.
     */
    public final double hardness;

    /**
     * Ob Minecraft den Block als Spitzhacken-Material klassifiziert.
     */
    public final boolean pickaxe;

    /**
     * Ob Minecraft den Block als Schaufel-Material klassifiziert.
     */
    public final boolean shovel;

    /**
     * Ob Minecraft den Block als Axt-Material klassifiziert.
     */
    public final boolean axe;

    /**
     * Ob Minecraft den Block als Hacken-Material klassifiziert.
     */
    public final boolean hoe;

    private MaterialPhysics(
            double mass,
            double cohesion,
            double fragmentation,
            double mobility,
            double lateralSpread,
            double hardness,
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
        this.pickaxe = pickaxe;
        this.shovel = shovel;
        this.axe = axe;
        this.hoe = hoe;
    }

    /**
     * Analysiert einen Block ausschließlich anhand seiner
     * Minecraft-Eigenschaften.
     */
    public static MaterialPhysics analyze(
            ServerLevel level,
            BlockPos pos,
            BlockState state
    ) {
        double rawHardness = state.getDestroySpeed(level, pos);

        if (rawHardness < 0.0) {
            return new MaterialPhysics(
                    1.0,
                    1.0,
                    0.0,
                    0.0,
                    0.0,
                    1.0,
                    false,
                    false,
                    false,
                    false
            );
        }

        /*
         * Minecraft-Härte ist nicht physikalisch identisch mit Dichte.
         * Sie ist aber ein sehr brauchbarer Indikator dafür, wie viel
         * Energie notwendig ist, um Material aus dem Boden zu lösen.
         *
         * Logarithmische Normalisierung verhindert, dass extrem harte
         * Blöcke alles dominieren.
         */
        double hardness = normalizeHardness(rawHardness);

        boolean pickaxe = state.is(BlockTags.MINEABLE_WITH_PICKAXE);
        boolean shovel = state.is(BlockTags.MINEABLE_WITH_SHOVEL);
        boolean axe = state.is(BlockTags.MINEABLE_WITH_AXE);
        boolean hoe = state.is(BlockTags.MINEABLE_WITH_HOE);

        /*
         * Werkzeugtyp beeinflusst die angenommene Materialstruktur.
         *
         * Schaufel:
         *   eher loses / granuläres Material
         *
         * Spitzhacke:
         *   eher kompaktes mineralisches Material
         *
         * Axt:
         *   eher faseriges organisches Material
         *
         * Hacke:
         *   eher Pflanzen / weiches organisches Material
         */
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

        /*
         * "Mass" ist absichtlich nicht einfach hardness.
         *
         * Ein harter Block soll nicht automatisch physikalisch schwer
         * sein. Für die Ejecta-Simulation brauchen wir hier vielmehr
         * eine Näherung dafür, wie stark er sich unter Impaktenergie
         * bewegt.
         */
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

        /*
         * 1.0 wird bereits bei relativ moderaten Härten erreicht,
         * ohne dass normale Minecraft-Blöcke alle bei ~0 landen.
         */
        return clamp01(Math.log1p(hardness) / Math.log1p(50.0));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}