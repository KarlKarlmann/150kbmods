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
    public static final ForgeConfigSpec.BooleanValue ALLOW_STARVATION_UNTAMING;

    // Cache prevents repeated string splitting and GC overhead during server ticks
    private static Set<String> activeChannels = new HashSet<>();
    private static boolean logAll = false;

    static {
        BUILDER.push("logging");

        LOG_CHANNELS = BUILDER
                .comment("Active diagnostic channel names separated by semicolons (e.g. \"DISMOUNT;NAVIGATION;AI\", \"ALL\" or \"\").",
                         "Enables granular diagnostic tracking without flooding log outputs.")
                .define("channels", "");

        BUILDER.pop();

        BUILDER.push("gameplay");

        ALLOW_STARVATION_UNTAMING = BUILDER
                .comment("Controls whether dragons lose affection and revert to wild status upon starvation.",
                         "Default: false (prevents in-flight dismounts and corrupted NBT taming states).")
                .define("allowStarvationUntaming", false);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private DragonColoniesConfig() {}

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

    public static boolean isStarvationUntamingAllowed() {
        return ALLOW_STARVATION_UNTAMING.get();
    }

    public static boolean isLogging(String channel) {
        return logAll || activeChannels.contains(channel);
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