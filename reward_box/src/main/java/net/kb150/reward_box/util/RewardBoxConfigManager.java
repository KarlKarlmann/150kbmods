package net.kb150.reward_box.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.kb150.reward_box.RewardBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RewardBoxConfigManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<String, BoxDefinition> BOX_DEFINITIONS = new HashMap<>();

    public record GuaranteedItemRule(String itemId, int minTier, int maxTier, int count, int countPerTier, String nbt) {}
    public record LootPoolRule(int minTier, List<ResourceLocation> tables) {}
    public record CrazyItemsRule(int minTier, boolean enchantScaling) {}
    public record BoxDefinition(
        String boxId,
        ResourceLocation texture,
        ResourceLocation glowTexture,
        int lockDurationSeconds,
        String breakDropItem,
        int breakDropCount,
        List<GuaranteedItemRule> guaranteedItems,
        List<LootPoolRule> vanillaLootPools,
        CrazyItemsRule crazyModItems
    ) {}

    public static java.util.Set<String> getLoadedBoxIds() {
        return BOX_DEFINITIONS.keySet();
    }

    public RewardBoxConfigManager() {
        super(GSON, "reward_boxes");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager resourceManager, ProfilerFiller profiler) {
        BOX_DEFINITIONS.clear();

        for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
            ResourceLocation fileLoc = entry.getKey();
            try {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject root = entry.getValue().getAsJsonObject();

                String boxId = root.get("box_id").getAsString();
                ResourceLocation texture = root.has("texture") 
                    ? new ResourceLocation(root.get("texture").getAsString()) 
                    : null;
                    
                ResourceLocation glowTexture = root.has("glow_texture") 
                    ? new ResourceLocation(root.get("glow_texture").getAsString()) 
                    : null;

                List<GuaranteedItemRule> guaranteed = new ArrayList<>();
                if (root.has("guaranteed_items") && root.get("guaranteed_items").isJsonArray()) {
                    for (JsonElement elem : root.getAsJsonArray("guaranteed_items")) {
                        JsonObject obj = elem.getAsJsonObject();
                        String item = obj.get("item").getAsString();
                        int minTier = obj.has("min_tier") ? obj.get("min_tier").getAsInt() : 1;
                        int maxTier = obj.has("max_tier") ? obj.get("max_tier").getAsInt() : Integer.MAX_VALUE;
                        int count = obj.has("count") ? obj.get("count").getAsInt() : 0;
                        int countPerTier = obj.has("count_per_tier") ? obj.get("count_per_tier").getAsInt() : 0;
                        String nbt = obj.has("nbt") ? obj.get("nbt").getAsString() : null;
                        guaranteed.add(new GuaranteedItemRule(item, minTier, maxTier, count, countPerTier, nbt));
                    }
                }

                List<LootPoolRule> pools = new ArrayList<>();
                if (root.has("vanilla_loot_pools") && root.get("vanilla_loot_pools").isJsonArray()) {
                    for (JsonElement elem : root.getAsJsonArray("vanilla_loot_pools")) {
                        JsonObject obj = elem.getAsJsonObject();
                        int minTier = obj.has("min_tier") ? obj.get("min_tier").getAsInt() : 1;
                        List<ResourceLocation> tables = new ArrayList<>();
                        if (obj.has("tables") && obj.get("tables").isJsonArray()) {
                            for (JsonElement tElem : obj.getAsJsonArray("tables")) {
                                tables.add(new ResourceLocation(tElem.getAsString()));
                            }
                        }
                        pools.add(new LootPoolRule(minTier, tables));
                    }
                }

                CrazyItemsRule crazy = null;
                if (root.has("crazy_mod_items") && root.get("crazy_mod_items").isJsonObject()) {
                    JsonObject obj = root.getAsJsonObject("crazy_mod_items");
                    int minTier = obj.has("min_tier") ? obj.get("min_tier").getAsInt() : 10;
                    boolean enchant = obj.has("enchant_scaling") && obj.get("enchant_scaling").getAsBoolean();
                    crazy = new CrazyItemsRule(minTier, enchant);
                }

                int lockDuration = root.has("lock_duration_seconds") ? root.get("lock_duration_seconds").getAsInt() : 300;
                String breakDropItem = root.has("break_drop_item") 
                    ? root.get("break_drop_item").getAsString() 
                    : null;

                int breakDropCount = root.has("break_drop_count") 
                    ? root.get("break_drop_count").getAsInt() 
                    : -1;

                BoxDefinition def = new BoxDefinition(
                    boxId, texture, glowTexture, lockDuration, breakDropItem, breakDropCount, guaranteed, pools, crazy
                );
                BOX_DEFINITIONS.put(boxId, def);
                RewardBox.LOGGER.info("[RewardBox] Kisten-Definition geladen: {}", boxId);

            } catch (Exception e) {
                RewardBox.LOGGER.error("[RewardBox] Fehler beim Laden von Kisten-JSON {}: {}", fileLoc, e.getMessage());
            }
        }
    }

    public static BoxDefinition getDefinition(String boxId) {
        return BOX_DEFINITIONS.get(boxId);
    }

    public static ResourceLocation getTextureForBox(String boxId) {
        BoxDefinition def = BOX_DEFINITIONS.get(boxId);
        return def != null ? def.texture() : null;
    }

    public static ResourceLocation getGlowTextureForBox(String boxId) {
        BoxDefinition def = BOX_DEFINITIONS.get(boxId);
        return def != null ? def.glowTexture() : null;
    }
}