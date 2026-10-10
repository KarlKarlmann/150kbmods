package net.kb150.woerp.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class WoerpConfig {
    public static final ForgeConfigSpec SERVER_SPEC;
    
    public static final ForgeConfigSpec.IntValue HALF_LIFE_TICKS;
    public static final ForgeConfigSpec.DoubleValue EPSILON;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("radiation");
        
        // Verhindert Datenbank-Leichen: Bestimmt, ab wann ein Emitter als restlos zerfallen gilt und aus dem NBT gelöscht wird.
        HALF_LIFE_TICKS = builder.comment("Halbwertszeit in Ticks (12000 = 10 Minuten)")
                .defineInRange("halfLifeTicks", 12000, 20, Integer.MAX_VALUE);
                
        // Schwellenwert: Strahlung unter diesem Wert wird mathematisch als 0 betrachtet.
        EPSILON = builder.comment("Abschalt-Schwelle für Emitter-Cleanup")
                .defineInRange("epsilon", 0.0001, 0.000001, 0.1);
        
        builder.pop();

        SERVER_SPEC = builder.build();
    }
}