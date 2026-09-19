package net.kb150.survivorcolonies.client;

import net.kb150.survivorcolonies.SurvivorColonies;
import net.kb150.survivorcolonies.client.model.SurvivorModel;
import net.kb150.survivorcolonies.client.model.SurvivorModelLayers;
import net.kb150.survivorcolonies.client.renderer.SurvivorRenderer;
import net.kb150.survivorcolonies.entity.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SurvivorColonies.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    // Unser neuer API-Schalter!
    public static boolean disableDefaultRenderer = false;

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        if (disableDefaultRenderer) {
            return;
        }
        event.registerEntityRenderer(ModEntities.SURVIVOR.get(), SurvivorRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        if (disableDefaultRenderer) return; // Optimiere: Layer brauchen wir dann auch nicht
        event.registerLayerDefinition(SurvivorModelLayers.SURVIVOR_STEVE, SurvivorModel::createSteveLayer);
        event.registerLayerDefinition(SurvivorModelLayers.SURVIVOR_ALEX, SurvivorModel::createAlexLayer);
    }
}