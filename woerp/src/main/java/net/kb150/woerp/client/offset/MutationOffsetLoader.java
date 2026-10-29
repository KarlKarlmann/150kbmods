package net.kb150.woerp.client.offset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kb150.woerp.Woerp;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

public class MutationOffsetLoader extends SimplePreparableReloadListener<Map<ResourceLocation, JsonElement>> {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<ResourceLocation, Map<String, MutationOffset>> OFFSETS = new HashMap<>();
    private static final Map<ResourceLocation, JsonObject> RAW_JSONS = new HashMap<>();

    public static final MutationOffsetLoader INSTANCE = new MutationOffsetLoader();

    @Override
    protected Map<ResourceLocation, JsonElement> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonElement> map = new HashMap<>();
        String dir = "offsets";

        for (Map.Entry<ResourceLocation, Resource> entry : resourceManager.listResources(dir, rl -> rl.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation fullRl = entry.getKey();
            if (!fullRl.getNamespace().equals(Woerp.MODID)) continue;

            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement json = GSON.fromJson(reader, JsonElement.class);
                String path = fullRl.getPath();
                String sub = path.substring(dir.length() + 1, path.length() - 5);
                int slash = sub.indexOf('/');
                if (slash != -1) {
                    map.put(new ResourceLocation(sub.substring(0, slash), sub.substring(slash + 1)), json);
                }
            } catch (Exception e) {
                Woerp.LOGGER.error("[WOERP-OFFSET] Fehler beim Parsen von: " + fullRl, e);
            }
        }
        return map;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager resourceManager, ProfilerFiller profiler) {
        OFFSETS.clear();
        RAW_JSONS.clear();

        map.forEach((entityRl, jsonElement) -> {
            if (!jsonElement.isJsonObject()) return;
            JsonObject root = jsonElement.getAsJsonObject();
            RAW_JSONS.put(entityRl, root);

            Map<String, MutationOffset> mutationMap = new HashMap<>();
            for (String key : root.keySet()) {
                JsonElement el = root.get(key);
                if (el.isJsonObject()) {
                    mutationMap.put(key, parseOffset(el.getAsJsonObject()));
                }
            }
            OFFSETS.put(entityRl, mutationMap);
        });
        Woerp.LOGGER.info("[WOERP-OFFSET] {} Offset-Profile aus Packs geladen.", OFFSETS.size());
    }

    private static MutationOffset parseOffset(JsonObject j) {
        String bone = j.has("bone") ? j.get("bone").getAsString() : "";
        float x = j.has("x") ? j.get("x").getAsFloat() : 0.0f;
        float y = j.has("y") ? j.get("y").getAsFloat() : 0.0f;
        float z = j.has("z") ? j.get("z").getAsFloat() : 0.0f;
        float sx = j.has("scale_x") ? j.get("scale_x").getAsFloat() : (j.has("scale") ? j.get("scale").getAsFloat() : 1.0f);
        float sy = j.has("scale_y") ? j.get("scale_y").getAsFloat() : (j.has("scale") ? j.get("scale").getAsFloat() : 1.0f);
        float sz = j.has("scale_z") ? j.get("scale_z").getAsFloat() : (j.has("scale") ? j.get("scale").getAsFloat() : 1.0f);
        float rx = j.has("rotation_x") ? j.get("rotation_x").getAsFloat() : 0.0f;
        float ry = j.has("rotation_y") ? j.get("rotation_y").getAsFloat() : 0.0f;
        float rz = j.has("rotation_z") ? j.get("rotation_z").getAsFloat() : 0.0f;
        return new MutationOffset(bone, x, y, z, sx, sy, sz, rx, ry, rz);
    }

    public static MutationOffset getOffset(ResourceLocation entityType, String mutationId) {
        Map<String, MutationOffset> m = OFFSETS.get(entityType);
        if (m != null && m.containsKey(mutationId)) return m.get(mutationId);
        return getDefaultOffset(mutationId);
    }

    public static JsonObject getRawJson(ResourceLocation entityType) {
        return RAW_JSONS.computeIfAbsent(entityType, k -> new JsonObject());
    }

    public static void updateOffsetAtRuntime(ResourceLocation entityType, String mutationId, MutationOffset newOffset) {
        OFFSETS.computeIfAbsent(entityType, k -> new HashMap<>()).put(mutationId, newOffset);
    }

    // Liefert feste Basisausrichtungen, damit Mobs ohne JSON-Datei niemals mit schwebenden Mutationen spawnen
    public static MutationOffset getDefaultOffset(String mutationId) {
        return switch (mutationId) {
            case "mutation_horn" -> new MutationOffset("head", -0.30f, -0.45f, -0.10f, 1f, 1f, 1f, 0, 0, 0);
            case "mutation_eyestalk" -> new MutationOffset("head", 0.32f, -0.40f, -0.15f, 1f, 1f, 1f, 0, 0, 0);
            case "mutation_visage" -> new MutationOffset("head", 0.0f, 0.0f, 0.0f, 1f, 1f, 1f, 0, 0, 0);
            case "mutation_second_face" -> new MutationOffset("body", -0.22f, 0.15f, -0.18f, 1f, 1f, 1f, 0, 15f, 0);
            case "mutation_pustules" -> new MutationOffset("body", 0.25f, 0.20f, 0.12f, 1f, 1f, 1f, 0, 0, 0);
            case "mutation_crest" -> new MutationOffset("body", 0.0f, 0.05f, 0.18f, 1f, 1f, 1f, 0, 0, 0);
            case "mutation_spur" -> new MutationOffset("right_arm", 0.0f, 0.25f, 0.15f, 1f, 1f, 1f, 0, 0, 0);
            case "mutation_carapace" -> new MutationOffset("body", -0.28f, -0.05f, 0.0f, 1f, 1f, 1f, 0, 0, 0);
            case "mutation_clubfoot" -> new MutationOffset("left_leg", 0.0f, 0.20f, 0.0f, 1f, 1f, 1f, 0, 0, 0);
            case "mutation_maw" -> new MutationOffset("head", 0.0f, 0.0f, -0.15f, 1f, 1f, 1f, 0, 0, 0);
            default -> new MutationOffset("head", 0, 0, 0, 1f, 1f, 1f, 0, 0, 0);
        };
    }

    public static class MutationOffset {
        public final String bone;
        public final float x, y, z;
        public final float scaleX, scaleY, scaleZ;
        public final float rotX, rotY, rotZ;

        public MutationOffset(String bone, float x, float y, float z, float scaleX, float scaleY, float scaleZ, float rotX, float rotY, float rotZ) {
            this.bone = bone != null ? bone : "";
            this.x = x; this.y = y; this.z = z;
            this.scaleX = scaleX; this.scaleY = scaleY; this.scaleZ = scaleZ;
            this.rotX = rotX; this.rotY = rotY; this.rotZ = rotZ;
        }
    }
}