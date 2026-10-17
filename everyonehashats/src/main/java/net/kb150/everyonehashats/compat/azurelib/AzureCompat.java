package net.kb150.everyonehashats.compat.azurelib;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import mod.azure.azurelib.render.entity.AzEntityRenderer;
import mod.azure.azurelib.render.AzRendererConfig;
import mod.azure.azurelib.render.layer.AzRenderLayer;
import net.kb150.everyonehashats.EveryoneHasHats;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.List;
import java.util.Set;

public class AzureCompat {

    public static void addAzureLayers(Set<EntityRenderer<?>> processedRenderers) {
        for (EntityRenderer<?> renderer : Minecraft.getInstance().getEntityRenderDispatcher().renderers.values()) {
            try {
                if (renderer instanceof AzEntityRenderer<?> azRenderer) {
                    if (processedRenderers.add(azRenderer)) {
                        AzRendererConfig<?, ?> config = azRenderer.config();
                        
                        // Reflektiver Zugriff auf die versiegelte Layer-Liste
                        Field layersField = AzRendererConfig.class.getDeclaredField("renderLayers");
                        layersField.setAccessible(true);
                        
                        @SuppressWarnings("unchecked")
                        List<AzRenderLayer<?, ?>> currentLayers = (List<AzRenderLayer<?, ?>>) layersField.get(config);
                        
                        // Check gegen Doppel-Injections
                        boolean hasHatLayer = currentLayers.stream().anyMatch(l -> l instanceof EveryoneHasHatsAzureLayer);
                        
						if (!hasHatLayer) {
                            List<AzRenderLayer<?, ?>> newLayers = new ArrayList<>(currentLayers);
                            newLayers.add(new EveryoneHasHatsAzureLayer<>());
                            layersField.set(config, Collections.unmodifiableList(newLayers));
                            
                            // DEBUG: Wurde es wirklich injiziert?
                            //EveryoneHasHats.LOGGER.info("[AZURE-COMPAT] Layer erfolgreich injiziert in: {}", azRenderer.getClass().getSimpleName());
                        }
                    }
                }
            } catch (Exception e) {
                //EveryoneHasHats.LOGGER.error("[EveryoneHasHats] Fehler beim Injizieren des AzureLib-Layers!", e);
            }
        }
    }
	public static boolean isAzureRenderer(EntityRenderer<?> renderer) {
        return renderer instanceof AzEntityRenderer;
    }

	public static Map<String, String> getAzureBones(EntityRenderer<?> renderer, net.minecraft.world.entity.LivingEntity entity) {
        Map<String, String> bones = new java.util.HashMap<>();
        try {
            if (renderer instanceof AzEntityRenderer<?> azRenderer) {
                // Der Provider ist protected, also brechen wir das Schloss mit Reflection auf
                Field providerField = AzEntityRenderer.class.getDeclaredField("provider");
                providerField.setAccessible(true);
                Object provider = providerField.get(azRenderer);
                
                // Signatur ist (Entity, Object) wegen Type-Erasure des generischen Parameters 'T'
                java.lang.reflect.Method provideModel = provider.getClass().getMethod("provideBakedModel", net.minecraft.world.entity.Entity.class, Object.class);
                Object bakedModel = provideModel.invoke(provider, entity, entity);
                
                if (bakedModel instanceof mod.azure.azurelib.model.AzBakedModel model) {
                    for (mod.azure.azurelib.model.AzBone bone : model.getBonesByName().values()) {
                        bones.put(bone.getName(), bone.getName()); 
                    }
                }
            }
        } catch (Exception e) {
            EveryoneHasHats.LOGGER.error("[AZURE-COMPAT] Fehler beim Laden der AzureLib-Bones", e);
        }
        return bones;
    }
}