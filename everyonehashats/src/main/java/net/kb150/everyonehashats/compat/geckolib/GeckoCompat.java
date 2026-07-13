package net.kb150.everyonehashats.compat.geckolib;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class GeckoCompat {

    public static boolean isGeckoRenderer(EntityRenderer<?> renderer) {
        return renderer instanceof GeoEntityRenderer;
    }

    public static void addGeckoLayers(Set<EntityRenderer<?>> processedRenderers) {
        for (EntityRenderer<?> renderer : Minecraft.getInstance().getEntityRenderDispatcher().renderers.values()) {
            try {
                if (renderer instanceof GeoEntityRenderer geoRenderer) {
                    // Markiere diesen Renderer als "verarbeitet", damit unser Vanilla-Code ihn ignoriert!
                    if (processedRenderers.add(geoRenderer)) {
                        // Injiziere unseren brandneuen Gecko-Hut-Layer
                        geoRenderer.addRenderLayer(new EveryoneHasHatsGeckoLayer<>(geoRenderer));
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    public static Map<String, String> getGeckoBones(EntityRenderer<?> renderer, LivingEntity entity) {
        Map<String, String> bones = new HashMap<>();
        try {
            if (renderer instanceof GeoEntityRenderer geoRenderer && entity instanceof GeoAnimatable animatable) {
                getBonesHelper(geoRenderer, animatable, bones);
            }
        } catch (Exception ignored) {} // Fallback, falls das Rendermodell noch nicht bereit ist
        return bones;
    }

    // Generic Helper um Type-Capture-Fehler zu vermeiden
    private static <T extends Entity & GeoAnimatable> void getBonesHelper(GeoEntityRenderer<T> renderer, GeoAnimatable animatable, Map<String, String> bones) {
        GeoModel<T> geoModel = renderer.getGeoModel();
        T castedAnimatable = (T) animatable;
        BakedGeoModel bakedModel = geoModel.getBakedModel(geoModel.getModelResource(castedAnimatable));

        // Scanne alle Knochen, die GeckoLib uns zur Verfügung stellt
        for (GeoBone topBone : bakedModel.topLevelBones()) {
            scanGeckoBones(topBone, "", bones);
        }
    }

    private static void scanGeckoBones(GeoBone bone, String prefix, Map<String, String> result) {
        // Bei GeckoLib reicht es für die `getBone()` Suche aus, wenn wir nur den Namen speichern, 
        // ABER für das Dropdown im UI zeigen wir den Pfad an, damit der Spieler sich orientieren kann!
        String path = prefix.isEmpty() ? bone.getName() : prefix + "/" + bone.getName();
        
        // Wir speichern: Key = UI-Anzeigename (Pfad), Value = Tatsächlicher Knochenname (fürs JSON)
        result.put(path, bone.getName());
        
        for (GeoBone child : bone.getChildBones()) {
            scanGeckoBones(child, path, result);
        }
    }
}