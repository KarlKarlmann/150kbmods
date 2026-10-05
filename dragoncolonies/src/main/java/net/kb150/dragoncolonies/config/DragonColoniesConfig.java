package net.kb150.dragoncolonies.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public final class DragonColoniesConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.ConfigValue<String> LOG_CHANNELS;

    // Cache verhindert String-Zerteilungen und GC-Druck im Server-Tick
    private static Set<String> activeChannels = new HashSet<>();
    private static boolean logAll = false;

    static {
        BUILDER.push("logging");

        LOG_CHANNELS = BUILDER
                .comment("Aktive Diagnose-Kanalnamen per Semikolon getrennt (z. B. \"DISMOUNT;NAVIGATION\", \"ALL\" oder \"\").",
                         "Fuer Bug-Reports einfach die benoetigten Kanalnamen eintragen lassen.")
                .define("channels", "DISMOUNT");

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private DragonColoniesConfig() {}

    public static void onConfigLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            updateCache();
        }
    }

    // Wandelt den Konfigurations-String in ein O(1) Lookup-Set um
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
        if (logAll) return true;
        return activeChannels.contains(channel);
    }

    public static boolean isLogAll() {
        return logAll;
    }

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

        boolean enabled;
        if (activeChannels.contains(channel)) {
            activeChannels.remove(channel);
            enabled = false;
        } else {
            activeChannels.add(channel);
            enabled = true;
        }
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
            if (enable) {
                activeChannels.add(channel);
            } else {
                activeChannels.remove(channel);
            }
        }
        saveToConfig();
    }

    public static void clearChannels() {
        activeChannels.clear();
        logAll = false;
        saveToConfig();
    }

    private static void saveToConfig() {
        String serialized = logAll ? "ALL" : String.join(";", activeChannels);
        LOG_CHANNELS.set(serialized);
        SPEC.save();
    }
}