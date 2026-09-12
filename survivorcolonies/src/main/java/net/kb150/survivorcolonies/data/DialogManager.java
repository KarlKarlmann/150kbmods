package net.kb150.survivorcolonies.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kb150.survivorcolonies.data.dialog.PlayerOption;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DialogManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();

    // RAM-Speicher für die Logik (Wird bei /reload geleert und neu befüllt)
    private static final Map<String, List<Integer>> NPC_LINES = new HashMap<>();
    private static final Map<Integer, List<PlayerOption>> PLAYER_OPTIONS = new HashMap<>();

    public DialogManager() {
        // Sucht im Ressourcen-Ordner nach: data/<beliebige_mod_id>/survivors/*.json
        super(GSON, "survivors");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objectMap, ResourceManager resourceManager, ProfilerFiller profiler) {
        NPC_LINES.clear();
        PLAYER_OPTIONS.clear();

        for (Map.Entry<ResourceLocation, JsonElement> entry : objectMap.entrySet()) {
            // Wir filtern nach der Datei "dialog_logic.json"
            if (entry.getKey().getPath().equals("dialog_logic")) {
                JsonObject root = entry.getValue().getAsJsonObject();

                // 1. NPC Lines einlesen
                if (root.has("npc_lines")) {
                    JsonObject npcLinesObj = root.getAsJsonObject("npc_lines");
                    for (String comboKey : npcLinesObj.keySet()) {
                        List<Integer> ids = new ArrayList<>();
                        npcLinesObj.getAsJsonArray(comboKey).forEach(el -> ids.add(el.getAsInt()));
                        NPC_LINES.put(comboKey, ids);
                    }
                }

                // 2. Player Options einlesen
                if (root.has("player_options")) {
                    JsonObject playerOptionsObj = root.getAsJsonObject("player_options");
                    for (String npcTextIdStr : playerOptionsObj.keySet()) {
                        int npcTextId = Integer.parseInt(npcTextIdStr);
                        List<PlayerOption> options = new ArrayList<>();
                        
                        playerOptionsObj.getAsJsonArray(npcTextIdStr).forEach(el -> {
                            JsonObject opt = el.getAsJsonObject();
                            options.add(new PlayerOption(
                                opt.get("option_id").getAsInt(),
                                opt.get("text_id").getAsInt(),
                                opt.get("stance").getAsString(),
                                opt.get("trust_delta").getAsInt(),
                                opt.get("once").getAsBoolean()
                            ));
                        });
                        PLAYER_OPTIONS.put(npcTextId, options);
                    }
                }
            }
        }
    }

    // --- Laufzeit-Zugriff für das GUI ---

    public static List<Integer> getNpcLineIds(String comboKey) {
        return NPC_LINES.getOrDefault(comboKey, List.of());
    }

    public static List<PlayerOption> getOptionsForNpcLine(int npcTextId) {
        return PLAYER_OPTIONS.getOrDefault(npcTextId, List.of());
    }
}