package net.kb150.grubies.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.*;

public class GrubiesConfig {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> LOG_CHANNELS;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLACKLISTED_EFFECTS;
    
    private static final Set<String> ACTIVE_CHANNELS = Collections.synchronizedSet(new HashSet<>());
    // Schnellzugriff ohne String-Allokationen im Ticking
    private static final Set<String> DISABLED_EFFECTS = Collections.synchronizedSet(new HashSet<>());

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        
        builder.push("logging");
        LOG_CHANNELS = builder.comment("Aktive Debug-Kanäle (z.B. LOOT, EAT, TRIP, ALL)")
                .defineList("active_channels", List.of("LOOT", "EAT", "TRIP"), o -> o instanceof String);
        builder.pop();

        builder.push("effects");
        BLACKLISTED_EFFECTS = builder.comment("Ausgeschlossene Effekte für Wurm-Combos (z.B. 'minecraft:instant_damage')")
                .defineList("blacklisted_effects", List.of("minecraft:instant_damage", "minecraft:instant_health"), o -> o instanceof String);
        builder.pop();

        SPEC = builder.build();
    }

    public static void onConfigLoad(ModConfigEvent event) {
        syncConfig();
    }

    private static void syncConfig() {
        ACTIVE_CHANNELS.clear();
        LOG_CHANNELS.get().forEach(s -> ACTIVE_CHANNELS.add(s.toUpperCase(Locale.ROOT)));

        DISABLED_EFFECTS.clear();
        BLACKLISTED_EFFECTS.get().forEach(s -> DISABLED_EFFECTS.add(s.toLowerCase(Locale.ROOT)));
    }

    public static boolean isLogging(String channel) {
        String ch = channel.toUpperCase(Locale.ROOT);
        return ACTIVE_CHANNELS.contains("ALL") || ACTIVE_CHANNELS.contains(ch);
    }

    // O(1) Set-Lookups zur Entlastung von Dedicated Servern bei hohen Spielerzahlen
    public static boolean isEffectBlacklisted(ResourceLocation loc) {
        return loc != null && DISABLED_EFFECTS.contains(loc.toString().toLowerCase(Locale.ROOT));
    }

    public static Set<String> getActiveChannels() {
        return Collections.unmodifiableSet(ACTIVE_CHANNELS);
    }

    public static boolean isLogAll() {
        return ACTIVE_CHANNELS.contains("ALL");
    }

    public static boolean toggleChannel(String channel) {
        String ch = channel.toUpperCase(Locale.ROOT);
        boolean enable = !ACTIVE_CHANNELS.contains(ch);
        setChannel(ch, enable);
        return enable;
    }

    public static void setChannel(String channel, boolean enable) {
        String ch = channel.toUpperCase(Locale.ROOT);
        if (enable) ACTIVE_CHANNELS.add(ch);
        else ACTIVE_CHANNELS.remove(ch);
        saveToConfig();
    }

    public static void clearChannels() {
        ACTIVE_CHANNELS.clear();
        saveToConfig();
    }

    private static void saveToConfig() {
        LOG_CHANNELS.set(new ArrayList<>(ACTIVE_CHANNELS));
        LOG_CHANNELS.save();
    }
}