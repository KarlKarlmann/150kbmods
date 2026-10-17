package net.kb150.everyonehashats;

import net.minecraftforge.common.ForgeConfigSpec;

public class EveryoneHasHatsConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue HAT_DROP_CHANCE;
    
    // Globale Rendering-Offsets zur Feinjustierung durch den Spieler
    public static final ForgeConfigSpec.DoubleValue GENERAL_HAT_Y_OFFSET;
    public static final ForgeConfigSpec.DoubleValue GENERAL_HAT_SCALE;

    static {
        BUILDER.push("EveryoneHasHats Einstellungen");

        HAT_DROP_CHANCE = BUILDER
                .comment("The probability that an entity will drop its hat upon death (0.0 = 0%, 1.0 = 100%)")
                .defineInRange("hatDropChance", 1.0D, 0.0D, 1.0D); // Standardmäßig auf 100%, damit manuell aufgesetzte Hüte nicht verloren gehen!

        GENERAL_HAT_Y_OFFSET = BUILDER
                .comment("Global Y-offset (height) for rendering hats on mobs. Higher values move hats upward.")
                .defineInRange("generalHatYOffset", 0.0D, -2.0D, 2.0D);

        GENERAL_HAT_SCALE = BUILDER
                .comment("Global scaling (size) of hats on mobs.")
                .defineInRange("generalHatScale", 1.0D, 0.1D, 3.0D);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}