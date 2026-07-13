package net.kb150.everyonehashats;

import net.minecraftforge.common.ForgeConfigSpec;

public class EveryoneHasHatsConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue SPAWN_WITH_HAT_CHANCE;
    public static final ForgeConfigSpec.DoubleValue HAT_DROP_CHANCE;
    
    // Globale Rendering-Offsets zur Feinjustierung durch den Spieler
    public static final ForgeConfigSpec.DoubleValue GENERAL_HAT_Y_OFFSET;
    public static final ForgeConfigSpec.DoubleValue GENERAL_HAT_SCALE;

    static {
        BUILDER.push("EveryoneHasHats Einstellungen");

        SPAWN_WITH_HAT_CHANCE = BUILDER
                .comment("Die Chance, mit der eine lebende Entity mit einem zufälligen Hut spawnt (0.0 = 0%, 1.0 = 100%)")
                .defineInRange("spawnWithHatChance", 0.05D, 0.0D, 1.0D);

        HAT_DROP_CHANCE = BUILDER
                .comment("Die Wahrscheinlichkeit, dass eine Entity ihren Hut beim Tod fallen lässt (0.0 = 0%, 1.0 = 100%)")
                .defineInRange("hatDropChance", 1.0D, 0.0D, 1.0D); // Standardmäßig auf 100%, damit manuell aufgesetzte Hüte nicht verloren gehen!

        GENERAL_HAT_Y_OFFSET = BUILDER
                .comment("Globaler Y-Offset (Höhe) für das Rendern der Hüte auf Mobs. Höhere Werte verschieben Hüte nach oben.")
                .defineInRange("generalHatYOffset", 0.0D, -2.0D, 2.0D);

        GENERAL_HAT_SCALE = BUILDER
                .comment("Globale Skalierung (Größe) der Hüte auf Mobs.")
                .defineInRange("generalHatScale", 1.0D, 0.1D, 3.0D);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}