package net.kb150.dragoncolonies.client.model;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.kb150.dragoncolonies.DragonColonies;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ermittelt Texturen für Custom-Berufe mit automatischem Pack-Fallback.
 * Verhindert Missing-Texture-Glitches (Schachbrettmuster), wenn Colony-Styles
 * keine eigenen Texturen für DragonColonies mitliefern.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = DragonColonies.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CitizenTextureHelper {

    // Verhindert frame-weise Festplatten- und I/O-Lookups im Render-Tick
    private static final Map<String, ResourceLocation> RESOLVED_TEXTURES = new ConcurrentHashMap<>();

    private CitizenTextureHelper() {}

    public static ResourceLocation getTexture(AbstractEntityCitizen citizen, String profession) {
        String style = citizen.getEntityData().get(AbstractEntityCitizen.DATA_STYLE);
        String suffix = citizen.getEntityData().get(AbstractEntityCitizen.DATA_TEXTURE_SUFFIX);
        String gender = citizen.isFemale() ? "female" : "male";
        int textureId = (citizen.getTextureId() % 1) + 1;

        String effectiveStyle = (style == null || style.isBlank()) ? "default" : style;
        String textureFile = profession + gender + textureId + (suffix != null ? suffix : "");
        String cacheKey = effectiveStyle + "/" + textureFile;

        return RESOLVED_TEXTURES.computeIfAbsent(cacheKey, k -> resolveFallback(effectiveStyle, textureFile, profession + gender + textureId));
    }

    private static ResourceLocation resolveFallback(String style, String textureFile, String baseTextureFile) {
        var manager = Minecraft.getInstance().getResourceManager();

        // 1. Primär: Textur im aktiven Style-Pack suchen
        ResourceLocation styleLocation = new ResourceLocation(DragonColonies.MOD_ID, "textures/entity/citizen/" + style + "/" + textureFile + ".png");
        if (manager.getResource(styleLocation).isPresent()) {
            return styleLocation;
        }

        // 2. Fallback: Standard-Skin im default-Pack
        ResourceLocation defaultLocation = new ResourceLocation(DragonColonies.MOD_ID, "textures/entity/citizen/default/" + textureFile + ".png");
        if (manager.getResource(defaultLocation).isPresent()) {
            return defaultLocation;
        }

        // 3. Sicherheitsnetz: Falls eine Suffix-Variante im Default-Pack fehlt, Basis-Skin ohne Suffix nutzen
        return new ResourceLocation(DragonColonies.MOD_ID, "textures/entity/citizen/default/" + baseTextureFile + ".png");
    }

    @SubscribeEvent
    public static void onRegisterReloadListener(RegisterClientReloadListenersEvent event) {
        // Leert den Textur-Cache automatisch, wenn der Spieler F3+T drückt oder Resourcepacks wechselt
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> RESOLVED_TEXTURES.clear());
    }
}