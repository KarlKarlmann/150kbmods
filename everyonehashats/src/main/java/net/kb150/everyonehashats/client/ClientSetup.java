package net.kb150.everyonehashats.client;

import net.kb150.everyonehashats.EveryoneHasHats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import com.mojang.blaze3d.vertex.PoseStack;

import java.lang.reflect.Field;
import java.util.*;

@Mod.EventBusSubscriber(modid = EveryoneHasHats.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void addEntityLayers(EntityRenderersEvent.AddLayers event) {
        Set<EntityRenderer<?>> processedRenderers = new HashSet<>();

        // 1. GECKOLIB SOFT-DEPENDENCY CHECK
        if (net.minecraftforge.fml.ModList.get().isLoaded("geckolib")) {
            try {
                net.kb150.everyonehashats.compat.geckolib.GeckoCompat.addGeckoLayers(processedRenderers);
            } catch (Exception e) {
                EveryoneHasHats.LOGGER.error("Fehler beim Laden des GeckoLib Compat-Moduls!", e);
            }
        }
		// 1.5 AZURELIB SOFT-DEPENDENCY CHECK
        if (net.minecraftforge.fml.ModList.get().isLoaded("azurelib")) {
            try {
                net.kb150.everyonehashats.compat.azurelib.AzureCompat.addAzureLayers(processedRenderers);
            } catch (Exception e) {
                EveryoneHasHats.LOGGER.error("Fehler beim Laden des AzureLib Compat-Moduls!", e);
            }
        }
        // 2. VANILLA COMPATIBILTIY
        for (EntityType<?> entityType : ForgeRegistries.ENTITY_TYPES) {
            try {
                // FIX: Unchecked Cast für das Event, damit der Compiler nicht meckert
                EntityRenderer<?> renderer = event.getRenderer((EntityType) entityType);
                
                // Wir überspringen den Renderer, falls GeckoCompat ihn oben schon ausgestattet hat!
                if (renderer instanceof LivingEntityRenderer livingRenderer && !processedRenderers.contains(livingRenderer)) {
                    if (processedRenderers.add(livingRenderer)) {
                        
                        boolean hasCustomHeadLayer = false;
                        for (Field field : LivingEntityRenderer.class.getDeclaredFields()) {
                            if (List.class.isAssignableFrom(field.getType())) {
                                try {
                                    field.setAccessible(true);
                                    List<?> layersList = (List<?>) field.get(livingRenderer);
                                    for (Object layer : layersList) {
                                        String className = layer.getClass().getSimpleName();
                                        // Prüft auf Vanilla Helme und Rüstungen unabhängig von Obfuscation
                                        if (className.contains("CustomHeadLayer") || className.contains("HumanoidArmorLayer")) {
                                            hasCustomHeadLayer = true;
                                            break;
                                        }
                                    }
                                    if (hasCustomHeadLayer) break;
                                } catch (Exception ignored) {}
                            }
                        }

                        // Registriere unsere Layer NUR bei Mobs, die KEIN eigenes Vanilla Head-Rendering besitzen!
                        if (!hasCustomHeadLayer) {
                            livingRenderer.addLayer(new EveryoneHasHatsLayer(livingRenderer));
                        }
                    }
                }
            } catch (Exception e) {
                // Überspringe fehlerhafte Modded-Entities
            }
        }

        // Registriert Client-Forge Event-Busse für Commands
        MinecraftForge.EVENT_BUS.register(ClientSetup.ForgeEvents.class);
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(HatOffsetLoader.INSTANCE);
    }

    public static class ForgeEvents {
        @SubscribeEvent
        public static void registerClientCommands(RegisterClientCommandsEvent event) {
            event.getDispatcher().register(Commands.literal("hatstudio")
                .executes(context -> {
                    Minecraft.getInstance().tell(() -> {
                        Minecraft.getInstance().setScreen(new HatStudioScreen());
                    });
                    return 1;
                })
            );
			event.getDispatcher().register(Commands.literal("hatdebuglayers")
				.executes(ctx -> {
					for (var type : java.util.List.of(EntityType.IRON_GOLEM, EntityType.WITCH)) {
						var mc = Minecraft.getInstance();
						var rl = ForgeRegistries.ENTITY_TYPES.getKey(type);
						var vanillaRenderer = mc.getEntityRenderDispatcher().renderers.get(type);

						if (!(vanillaRenderer instanceof LivingEntityRenderer<?, ?> living)) {
							EveryoneHasHats.LOGGER.info("[HAT-PROBE] {}: Kein LivingEntityRenderer vorhanden", rl);
							continue;
						}

						// Umgeht 'protected access', ohne das Projekt auf Access-Transformer umstellen zu muessen
						boolean inVanilla = false;
						for (java.lang.reflect.Field f : LivingEntityRenderer.class.getDeclaredFields()) {
							if (java.util.List.class.isAssignableFrom(f.getType())) {
								try {
									f.setAccessible(true);
									java.util.List<?> list = (java.util.List<?>) f.get(living);
									if (list != null) {
										inVanilla = list.stream().anyMatch(l -> l instanceof EveryoneHasHatsLayer);
										break;
									}
								} catch (Exception ignored) {}
							}
						}

						// Fragt Epic Fight vollstaendig entkoppelt ab, falls die Mod im Runtime-Classpath existiert
						if (net.minecraftforge.fml.ModList.get().isLoaded("epicfight")) {
							try {
								Class<?> engineCls = Class.forName("yesman.epicfight.client.ClientEngine");
								Object engineInstance = engineCls.getMethod("getInstance").invoke(null);
								Object renderEngine = engineCls.getField("renderEngine").get(engineInstance);
								Object efRenderer = renderEngine.getClass().getMethod("getEntityRenderer", EntityType.class).invoke(renderEngine, type);

								if (efRenderer != null) {
									Class<?> patchedCls = Class.forName("yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer");
									if (patchedCls.isInstance(efRenderer)) {
										java.lang.reflect.Field pLayersField = patchedCls.getDeclaredField("patchedLayers");
										pLayersField.setAccessible(true);
										java.util.Map<?, ?> map = (java.util.Map<?, ?>) pLayersField.get(efRenderer);
										boolean inEf = map.containsKey(EveryoneHasHatsLayer.class);

										EveryoneHasHats.LOGGER.info("[HAT-PROBE] {} -> Vanilla: {} | EF-Renderer: {} | In EF-Whitelist: {} (EF-Layers: {})",
											rl, inVanilla, efRenderer.getClass().getSimpleName(), inEf, map.size());
									} else {
										EveryoneHasHats.LOGGER.info("[HAT-PROBE] {} -> Vanilla: {} | EF-Renderer: {} (Nicht gepatcht)",
											rl, inVanilla, efRenderer.getClass().getSimpleName());
									}
								} else {
									EveryoneHasHats.LOGGER.info("[HAT-PROBE] {} -> Vanilla: {} | EF-Renderer: null", rl, inVanilla);
								}
							} catch (Exception e) {
								EveryoneHasHats.LOGGER.error("[HAT-PROBE] Reflection-Fehler bei Epic Fight fuer " + rl, e);
							}
						} else {
							EveryoneHasHats.LOGGER.info("[HAT-PROBE] {} -> Vanilla: {} | Epic Fight nicht aktiv", rl, inVanilla);
						}
					}
					return 1;
				})
			);
        }
    }

    public static class ModelBoneScanner {

        public static boolean applyBoneTransforms(EntityModel<?> model, String bonePath, PoseStack poseStack) {
            if (model == null || bonePath == null || bonePath.trim().isEmpty()) return false;

            String[] pathParts = bonePath.split("/");
            if (pathParts.length == 0) return false;

            ModelPart currentPart = null;
            Class<?> clazz = model.getClass();

            while (clazz != null && clazz != Object.class) {
                try {
                    Field field = clazz.getDeclaredField(pathParts[0]);
                    field.setAccessible(true);
                    currentPart = (ModelPart) field.get(model);
                    if (currentPart != null) break;
                } catch (Exception e) { /* weitersuchen */ }
                clazz = clazz.getSuperclass();
            }

            if (currentPart == null) return false;

            currentPart.translateAndRotate(poseStack);

            for (int i = 1; i < pathParts.length; i++) {
                Map<String, ModelPart> children = getChildrenOf(currentPart);
                if (children != null && children.containsKey(pathParts[i])) {
                    currentPart = children.get(pathParts[i]);
                    currentPart.translateAndRotate(poseStack);
                } else {
                    return false;
                }
            }
            return true;
        }

        private static Field cachedChildrenField;
        private static boolean childrenFieldResolved = false;

        @SuppressWarnings("unchecked")
        private static Map<String, ModelPart> getChildrenOf(ModelPart part) {
            if (!childrenFieldResolved) {
                childrenFieldResolved = true;
                for (Field field : ModelPart.class.getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        cachedChildrenField = field;
                        break;
                    }
                }
            }
            if (cachedChildrenField == null) return null;
            try {
                return (Map<String, ModelPart>) cachedChildrenField.get(part);
            } catch (Exception e) {
                return null;
            }
        }
    }
}