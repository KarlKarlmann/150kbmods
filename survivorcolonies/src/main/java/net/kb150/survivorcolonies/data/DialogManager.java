package net.kb150.survivorcolonies.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Laufzeit-Manager für den generierten Konversations-Graphen.
 * Lädt dialog_logic.json via Datapack-Reload und löst Optionen deterministisch auf.
 */
public class DialogManager extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();

    private static final List<Integer> ROOTS = new ArrayList<>();
    private static final Map<Integer, NpcReaction> NPC_REACTIONS = new HashMap<>();
    private static final Map<Integer, PlayerReaction> PLAYER_REACTIONS = new HashMap<>();

    public DialogManager() {
        super(GSON, "survivors");
    }

    @Override
    protected void apply(
            Map<ResourceLocation, JsonElement> objectMap,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {
        ROOTS.clear();
        NPC_REACTIONS.clear();
        PLAYER_REACTIONS.clear();

        for (Map.Entry<ResourceLocation, JsonElement> entry : objectMap.entrySet()) {
            try {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }

                JsonObject root = entry.getValue().getAsJsonObject();

                if (!root.has("roots") && !root.has("npc_reactions") && !root.has("player_reactions")) {
                    continue;
                }

                if (root.has("roots") && root.get("roots").isJsonArray()) {
                    JsonArray roots = root.getAsJsonArray("roots");
                    for (JsonElement element : roots) {
                        ROOTS.add(element.getAsInt());
                    }
                }

                if (root.has("npc_reactions") && root.get("npc_reactions").isJsonObject()) {
                    JsonObject npcObject = root.getAsJsonObject("npc_reactions");
                    for (String idString : npcObject.keySet()) {
                        int id = Integer.parseInt(idString);
                        JsonObject value = npcObject.getAsJsonObject(idString);

                        Map<String, JsonElement> conditions = new HashMap<>();
                        if (value.has("conditions") && value.get("conditions").isJsonObject()) {
                            JsonObject conditionObject = value.getAsJsonObject("conditions");
                            for (String key : conditionObject.keySet()) {
                                conditions.put(key, conditionObject.get(key));
                            }
                        }

                        List<Integer> optionIds = readIntArray(value, "options");

                        String textOrKey = "";
                        if (value.has("text_key")) {
                            textOrKey = value.get("text_key").getAsString();
                        } else if (value.has("text")) {
                            textOrKey = value.get("text").getAsString();
                        }

                        int trustDelta = value.has("trust_delta") ? value.get("trust_delta").getAsInt() : 0;

                        NPC_REACTIONS.put(id, new NpcReaction(
                                id,
                                textOrKey,
                                conditions,
                                trustDelta,
                                optionIds
                        ));
                    }
                }

                if (root.has("player_reactions") && root.get("player_reactions").isJsonObject()) {
                    JsonObject playerObject = root.getAsJsonObject("player_reactions");
                    for (String idString : playerObject.keySet()) {
                        int id = Integer.parseInt(idString);
                        JsonObject value = playerObject.getAsJsonObject(idString);

                        String textOrKey = "";
                        if (value.has("text_key")) {
                            textOrKey = value.get("text_key").getAsString();
                        } else if (value.has("text")) {
                            textOrKey = value.get("text").getAsString();
                        }

                        String style = value.has("style") ? value.get("style").getAsString() : "";
                        List<Integer> optionIds = readIntArray(value, "options");

                        PLAYER_REACTIONS.put(id, new PlayerReaction(
                                id,
                                textOrKey,
                                style,
                                optionIds
                        ));
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Fehler beim Laden der Dialog-Datei '{}': {}", entry.getKey(), e.getMessage());
            }
        }

        LOGGER.info("[SurvivorColonies] Erfolgreich geladen: {} NPC-Reaktionen, {} Spieler-Reaktionen, {} Roots.",
                NPC_REACTIONS.size(), PLAYER_REACTIONS.size(), ROOTS.size());
    }

    private static List<Integer> readIntArray(JsonObject object, String key) {
        List<Integer> result = new ArrayList<>();
        if (!object.has(key) || !object.get(key).isJsonArray()) {
            return result;
        }

        for (JsonElement element : object.getAsJsonArray(key)) {
            result.add(element.getAsInt());
        }
        return result;
    }

    public static List<Integer> getRoots() {
        return List.copyOf(ROOTS);
    }

    public static NpcReaction getNpcReaction(int id) {
        return NPC_REACTIONS.get(id);
    }

    public static PlayerReaction getPlayerReaction(int id) {
        return PLAYER_REACTIONS.get(id);
    }

    /**
     * Wählt deterministisch die NPC-Reaktion aus, die auf eine Spieler-Antwort folgt.
     * Prüft Bedingungen gegen das Persönlichkeits- und Werteprofil des Survivors.
     */
    public static NpcReaction resolvePlayerReaction(
            SurvivorEntity survivor,
            int playerReactionId
    ) {
        PlayerReaction playerReaction = PLAYER_REACTIONS.get(playerReactionId);
        if (playerReaction == null) {
            return null;
        }

        List<NpcReaction> available = new ArrayList<>();
        NpcReaction defaultReaction = null;

        for (int npcReactionId : playerReaction.options()) {
            NpcReaction reaction = NPC_REACTIONS.get(npcReactionId);
            if (reaction == null) {
                continue;
            }

            if (reaction.isDefault()) {
                if (defaultReaction == null) {
                    defaultReaction = reaction;
                }
                continue;
            }

            if (conditionsMatch(survivor, reaction.conditions())) {
                available.add(reaction);
            }
        }

        if (defaultReaction != null) {
            available.add(defaultReaction);
        } else if (available.isEmpty() && !playerReaction.options().isEmpty()) {
            NpcReaction fallback = NPC_REACTIONS.get(playerReaction.options().get(0));
            if (fallback != null) {
                return fallback;
            }
        }

        if (available.isEmpty()) {
            for (int optId : playerReaction.options()) {
                NpcReaction fallback = NPC_REACTIONS.get(optId);
                if (fallback != null) return fallback;
            }
            return null;
        }

        if (available.size() == 1) {
            return available.get(0);
        }

        int selectedIndex = StableHash.variantPick(
                available.size(),
                survivor.getUUID(),
                "player_reaction:" + playerReactionId,
                "npc_reaction_selection"
        );

        return available.get(selectedIndex);
    }

    private static boolean conditionsMatch(
            SurvivorEntity survivor,
            Map<String, JsonElement> conditions
    ) {
        UUID uuid = survivor.getUUID();

        for (Map.Entry<String, JsonElement> entry : conditions.entrySet()) {
            String type = entry.getKey();
            JsonElement value = entry.getValue();

            switch (type) {
                case "tone" -> {
                    if (!value.isJsonPrimitive()
                            || !SurvivorPersonality.getTone(uuid).equalsIgnoreCase(value.getAsString())) {
                        return false;
                    }
                }
                case "backstory" -> {
                    if (!value.isJsonPrimitive()
                            || !SurvivorPersonality.getBackstory(uuid).equalsIgnoreCase(value.getAsString())) {
                        return false;
                    }
                }
                case "motivation" -> {
                    if (!value.isJsonPrimitive()
                            || !SurvivorPersonality.getMotivation(uuid).equalsIgnoreCase(value.getAsString())) {
                        return false;
                    }
                }
                case "trust" -> {
                    if (!value.isJsonObject() || !trustConditionMatches(survivor.getTrust(), value.getAsJsonObject())) {
                        return false;
                    }
                }
                case "skill" -> {
                    if (!value.isJsonObject() || !skillConditionMatches(survivor, value.getAsJsonObject())) {
                        return false;
                    }
                }
                default -> {
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean trustConditionMatches(int trust, JsonObject condition) {
        if (condition.has("min") && trust < condition.get("min").getAsInt()) {
            return false;
        }
        if (condition.has("max") && trust > condition.get("max").getAsInt()) {
            return false;
        }
        return condition.has("min") || condition.has("max");
    }

    private static boolean skillConditionMatches(
            SurvivorEntity survivor,
            JsonObject condition
    ) {
        if (condition.size() == 0) {
            return false;
        }

        String skillName = condition.keySet().iterator().next();
        JsonElement skillRule = condition.get(skillName);
        if (!skillRule.isJsonObject()) {
            return false;
        }

        Map<String, Integer> skills = survivor.getSkills();
        int actual = skills.getOrDefault(skillName, 1);

        JsonObject rule = skillRule.getAsJsonObject();
        if (rule.has("min") && actual < rule.get("min").getAsInt()) {
            return false;
        }
        if (rule.has("max") && actual > rule.get("max").getAsInt()) {
            return false;
        }
        return rule.has("min") || rule.has("max");
    }

    public record NpcReaction(
            int id,
            String text,
            Map<String, JsonElement> conditions,
            int trustDelta,
            List<Integer> options
    ) {
        public String textKey() {
            return text;
        }

        public boolean isDefault() {
            return conditions.isEmpty();
        }
    }

    public record PlayerReaction(
            int id,
            String text,
            String style,
            List<Integer> options
    ) {
        public String textKey() {
            return text;
        }
    }
}