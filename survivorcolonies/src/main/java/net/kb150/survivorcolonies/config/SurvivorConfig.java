package net.kb150.survivorcolonies.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class SurvivorConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ALLOW_TAVERN_VISITORS;
    public static final ForgeConfigSpec.IntValue CHILD_SPAWN_CHANCE_DENOMINATOR;
    private static final ForgeConfigSpec.ConfigValue<String> LOG_CHANNELS;

    // In-Memory-Cache verhindert String-Splitting und GC-Overhead bei Server-Ticks
    private static Set<String> activeChannels = new HashSet<>();
    private static boolean logAll = false;

    static {
        BUILDER.push("Logging");
        LOG_CHANNELS = BUILDER
            .comment("Aaktive Diagnose-Kanäle getrennt durch Semikolon (z.B. \"TENT;AI;DIALOG\", \"ALL\" oder \"\").")
            .define("channels", "");
        BUILDER.pop();

        BUILDER.push("Spawning");
        ALLOW_TAVERN_VISITORS = BUILDER
            .comment("Sollen in MineColonies-Tavernen normale Besucher spawnen? (Standard: false)")
            .define("allowTavernVisitors", false);

        CHILD_SPAWN_CHANCE_DENOMINATOR = BUILDER
            .comment("Geburten-Chance als 1 zu X Verhältnis. 1 = Jeder Versuch klappt (Vanilla). 100 = 1 zu 100 Chance. 0 = Deaktiviert.")
            .defineInRange("childSpawnChanceDenominator", 100, 0, 100000);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public static void onConfigLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            updateCache();
        }
    }

    private static void updateCache() {
        String raw = LOG_CHANNELS.get();
        if (raw == null || raw.isBlank()) {
            activeChannels = new HashSet<>();
            logAll = false;
            return;
        }
        logAll = raw.contains("ALL");
        activeChannels = new HashSet<>(Arrays.asList(raw.toUpperCase().split("\\s*;\\s*")));
    }

    public static boolean isLogging(String channel) {
        return logAll || activeChannels.contains(channel.toUpperCase());
    }

    public static boolean isLogAll() { return logAll; }

    public static Set<String> getActiveChannels() {
        return Collections.unmodifiableSet(new TreeSet<>(activeChannels));
    }

    public static boolean toggleChannel(String rawChannel) {
        String channel = rawChannel.trim().toUpperCase();
        if ("ALL".equals(channel)) {
            logAll = !logAll;
            if (logAll) activeChannels.add("ALL");
            else activeChannels.remove("ALL");
            saveToConfig();
            return logAll;
        }
        boolean enabled = activeChannels.contains(channel) ? !activeChannels.remove(channel) : activeChannels.add(channel);
        saveToConfig();
        return enabled;
    }

    public static void setChannel(String rawChannel, boolean enable) {
        String channel = rawChannel.trim().toUpperCase();
        if ("ALL".equals(channel)) {
            logAll = enable;
            if (enable) activeChannels.add("ALL");
            else activeChannels.remove("ALL");
        } else {
            if (enable) activeChannels.add(channel);
            else activeChannels.remove(channel);
        }
        saveToConfig();
    }

    public static void clearChannels() {
        activeChannels.clear();
        logAll = false;
        saveToConfig();
    }

    private static void saveToConfig() {
        LOG_CHANNELS.set(logAll ? "ALL" : String.join(";", activeChannels));
        SPEC.save();
    }
}