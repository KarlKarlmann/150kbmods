package net.kb150.survivorcolonies.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class SurvivorConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ALLOW_TAVERN_VISITORS;
    public static final ForgeConfigSpec.IntValue CHILD_SPAWN_CHANCE_DENOMINATOR;

    static {
        BUILDER.push("Spawning");
        
        // Blockiert oder erlaubt das Spawnen von Tavernen-Gästen
        ALLOW_TAVERN_VISITORS = BUILDER
            .comment("Sollen in MineColonies-Tavernen normale Besucher spawnen? (Standard: false)")
            .define("allowTavernVisitors", false);

        // Bestimmt die Chance X:1 gegen ein Kind (1 = 100% Chance / Vanilla, 100 = 1:100 Chance, 0 = Geburten komplett aus)
        CHILD_SPAWN_CHANCE_DENOMINATOR = BUILDER
            .comment("Geburten-Chance als 1 zu X Verhältnis. 1 = Jeder Versuch klappt (Vanilla). 100 = 1 zu 100 Chance. 0 = Geburten deaktiviert.")
            .defineInRange("childSpawnChanceDenominator", 100, 0, 100000);
            
        BUILDER.pop();
        
        SPEC = BUILDER.build();
    }
}