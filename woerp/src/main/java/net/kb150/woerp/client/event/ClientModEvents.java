package net.kb150.woerp.client.event;

import net.kb150.woerp.Woerp;
import net.kb150.woerp.client.layer.MutationLayer;
import net.kb150.woerp.client.layer.WoerpModelLayers;
import net.kb150.woerp.client.model.*;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.Set;

@Mod.EventBusSubscriber(modid = Woerp.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        // Bakt die Mesh-Definitionen beim Spielstart zentral in den EntityModelSet-Speicher
        event.registerLayerDefinition(WoerpModelLayers.HORN, HornModel::createBodyLayer);
        event.registerLayerDefinition(WoerpModelLayers.EYESTALK, EyestalkModel::createBodyLayer);
        event.registerLayerDefinition(WoerpModelLayers.SECOND_FACE, SecondFaceModel::createBodyLayer);
        event.registerLayerDefinition(WoerpModelLayers.PUSTULES, PustulesModel::createBodyLayer);
        event.registerLayerDefinition(WoerpModelLayers.CREST, CrestModel::createBodyLayer);
        event.registerLayerDefinition(WoerpModelLayers.SPUR, SpurModel::createBodyLayer);
        event.registerLayerDefinition(WoerpModelLayers.CARAPACE, CarapaceModel::createBodyLayer);
        event.registerLayerDefinition(WoerpModelLayers.CLUBFOOT, ClubfootModel::createBodyLayer);
        event.registerLayerDefinition(WoerpModelLayers.MAW, MawModel::createBodyLayer);

        Woerp.LOGGER.info("[WOERP-VFX] 9 ModelLayerDefinitions erfolgreich registriert.");
    }

    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        EntityModelSet modelSet = event.getEntityModels();
        Set<LivingEntityRenderer<?, ?>> processed = new HashSet<>();

        // 1. Alle lebenden Mobs (Tiere, Monster, Villager)
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES) {
            try {
                EntityRenderer<?> renderer = event.getRenderer((EntityType) type);
                if (renderer instanceof LivingEntityRenderer living && processed.add(living)) {
                    living.addLayer(new MutationLayer(living, modelSet));
                }
            } catch (Exception ignored) {}
        }

        // 2. Spieler-Modelle (Default Steve & Slim Alex)
        for (String skin : event.getSkins()) {
            LivingEntityRenderer<?, ?> skinRenderer = event.getSkin(skin);
            if (skinRenderer != null && processed.add(skinRenderer)) {
                skinRenderer.addLayer(new MutationLayer(skinRenderer, modelSet));
            }
        }

        Woerp.LOGGER.info("[WOERP-VFX] MutationLayer in {} LivingEntityRenderer injiziert.", processed.size());
    }
}