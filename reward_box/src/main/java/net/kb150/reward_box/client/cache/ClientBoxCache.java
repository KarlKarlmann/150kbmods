package net.kb150.reward_box.client.cache;

import net.minecraft.resources.ResourceLocation;
import java.util.HashMap;
import java.util.Map;

public class ClientBoxCache {
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();
    private static final Map<String, ResourceLocation> GLOW_TEXTURES = new HashMap<>(); // NEU

    public static void update(Map<String, String[]> syncedMap) {
        TEXTURES.clear();
        GLOW_TEXTURES.clear();
        for (Map.Entry<String, String[]> entry : syncedMap.entrySet()) {
            TEXTURES.put(entry.getKey(), new ResourceLocation(entry.getValue()[0]));
            if (entry.getValue()[1] != null && !entry.getValue()[1].isEmpty()) {
                GLOW_TEXTURES.put(entry.getKey(), new ResourceLocation(entry.getValue()[1]));
            }
        }
    }

    public static ResourceLocation getTexture(String boxId) {
        return TEXTURES.get(boxId);
    }

    public static ResourceLocation getGlowTexture(String boxId) {
        return GLOW_TEXTURES.get(boxId);
    }
}