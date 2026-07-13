package net.kb150.everyonehashats.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

public class HatOffsetLoader extends SimplePreparableReloadListener<Map<ResourceLocation, JsonElement>> {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<ResourceLocation, HatOffset> OFFSETS = new HashMap<>();
    private static final String MOD_ID = "everyonehashats";

    public static final HatOffsetLoader INSTANCE = new HatOffsetLoader();

    @Override
    protected Map<ResourceLocation, JsonElement> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonElement> map = new HashMap<>();
        String directory = "offsets";

        for (Map.Entry<ResourceLocation, Resource> entry : resourceManager.listResources(directory, rl -> rl.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation fullRl = entry.getKey();
            
            if (fullRl.getNamespace().equals(MOD_ID)) {
                try (Reader reader = entry.getValue().openAsReader()) {
                    JsonElement jsonElement = GSON.fromJson(reader, JsonElement.class);
                    
                    String path = fullRl.getPath();
                    String subPath = path.substring(directory.length() + 1, path.length() - 5);
                    
                    int slashIndex = subPath.indexOf('/');
                    if (slashIndex != -1) {
                        String entityNamespace = subPath.substring(0, slashIndex);
                        String entityPath = subPath.substring(slashIndex + 1);
                        ResourceLocation entityRl = new ResourceLocation(entityNamespace, entityPath);
                        map.put(entityRl, jsonElement);
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to parse hat offset JSON: " + fullRl, e);
                }
            }
        }
        return map;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager resourceManager, ProfilerFiller profiler) {
        OFFSETS.clear();
        map.forEach((entityRl, jsonElement) -> {
            try {
                if (jsonElement.isJsonObject()) {
                    JsonObject json = jsonElement.getAsJsonObject();
                    String bone = json.has("bone") ? json.get("bone").getAsString() : "";
                    float x = json.has("x") ? json.get("x").getAsFloat() : 0.0f;
                    float y = json.has("y") ? json.get("y").getAsFloat() : 0.0f;
                    float z = json.has("z") ? json.get("z").getAsFloat() : 0.0f;
                    
                    // Abwärtskompatibilität: Falls das alte "scale" genutzt wurde, übernehme es für alle 3 Achsen
                    float fallbackScale = json.has("scale") ? json.get("scale").getAsFloat() : 1.0f;
                    float scaleX = json.has("scale_x") ? json.get("scale_x").getAsFloat() : fallbackScale;
                    float scaleY = json.has("scale_y") ? json.get("scale_y").getAsFloat() : fallbackScale;
                    float scaleZ = json.has("scale_z") ? json.get("scale_z").getAsFloat() : fallbackScale;
                    
                    float rotX = json.has("rotation_x") ? json.get("rotation_x").getAsFloat() : 0.0f;
                    float rotY = json.has("rotation_y") ? json.get("rotation_y").getAsFloat() : 180.0f;
                    float rotZ = json.has("rotation_z") ? json.get("rotation_z").getAsFloat() : 0.0f;
                    
                    OFFSETS.put(entityRl, new HatOffset(bone, x, y, z, scaleX, scaleY, scaleZ, rotX, rotY, rotZ));
                }
            } catch (Exception e) {
                LOGGER.error("Failed to apply hat offset configuration for: " + entityRl, e);
            }
        });
        LOGGER.info("Successfully loaded " + OFFSETS.size() + " custom hat offset profiles.");
    }

    public static HatOffset getOffset(ResourceLocation entityType) {
        return OFFSETS.getOrDefault(entityType, HatOffset.DEFAULT);
    }

    // Erlaubt es dem Studio, den RAM sofort zu aktualisieren!
    public static void updateOffsetAtRuntime(ResourceLocation entityType, HatOffset newOffset) {
        OFFSETS.put(entityType, newOffset);
    }

    public static class HatOffset {
        public static final HatOffset DEFAULT = new HatOffset("", 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 180.0f, 0.0f);

        public final String bone;
        public final float x, y, z;
        public final float scaleX, scaleY, scaleZ;
        public final float rotX, rotY, rotZ;

        public HatOffset(String bone, float x, float y, float z, float scaleX, float scaleY, float scaleZ, float rotX, float rotY, float rotZ) {
            this.bone = bone;
            this.x = x;
            this.y = y;
            this.z = z;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.scaleZ = scaleZ;
            this.rotX = rotX;
            this.rotY = rotY;
            this.rotZ = rotZ;
        }
    }
}