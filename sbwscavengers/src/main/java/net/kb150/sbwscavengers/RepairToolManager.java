package net.kb150.sbwscavengers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = "sbwscavengers", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RepairToolManager extends SimpleJsonResourceReloadListener {

    public static final Logger LOGGER = LogManager.getLogger("RepairToolManager");
    private static final Gson GSON = new Gson();
    public static final List<String> TOOLS = new ArrayList<>();

    public RepairToolManager() {
        // Sucht nach JSONs in "data/<namespace>/repair_tools/"
        super(GSON, "repair_tools");
    }

    /*
     * STREAMING_CHUNK:Lade Werkzeuge aus dem Datapack und stelle sie der Mod bereit...
     */
    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objectIn, ResourceManager resourceManagerIn, ProfilerFiller profilerIn) {
        TOOLS.clear();
        
        for (Map.Entry<ResourceLocation, JsonElement> entry : objectIn.entrySet()) {
            // Wir zielen spezifisch auf "data/sbwscavengers/repair_tools/tools.json" ab
            if (entry.getKey().getNamespace().equals("sbwscavengers") && entry.getKey().getPath().equals("tools")) {
                try {
                    JsonObject root = entry.getValue().getAsJsonObject();
                    if (root.has("tools")) {
                        JsonArray toolsArray = root.getAsJsonArray("tools");
                        for (JsonElement elem : toolsArray) {
                            TOOLS.add(elem.getAsString());
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Fehler beim Verarbeiten von repair_tools/tools.json", e);
                }
            }
        }

        // Rückfall-Ebene, falls der Spieler das Datapack noch nicht installiert hat
        if (TOOLS.isEmpty()) {
            LOGGER.warn("Keine repair_tools/tools.json im Datapack gefunden! Lade Standard-Werkzeuge als Fallback.");
            TOOLS.add("minecraft:iron_pickaxe");
            TOOLS.add("minecraft:iron_hoe");
            TOOLS.add("minecraft:iron_axe");
        } else {
            LOGGER.info("Erfolgreich {} Reparatur-Werkzeuge aus dem Datapack geladen.", TOOLS.size());
        }
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new RepairToolManager());
    }
}