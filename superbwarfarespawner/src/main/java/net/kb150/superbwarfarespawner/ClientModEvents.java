package net.kb150.superbwarfarespawner.client;

import net.kb150.superbwarfarespawner.ModEntities;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/*
 * WARUM value = Dist.CLIENT: 
 * Diese Klasse darf NIEMALS auf dem Dedicated Server geladen werden, 
 * da Klassen wie "NoopRenderer" dort nicht existieren und zum Crash fuehren.
 */
@Mod.EventBusSubscriber(modid = "superbwarfarespawner", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        /*
         * STREAMING_CHUNK:Registriere Render-Dummies für alle 3 Profile...
         * WARUM: Verhindert Client-Crashes. Der NoopRenderer verschwendet 0% GPU,
         * da er einfach sofort "return" aufruft, wenn die Render-Engine ihn fragt.
         */
        event.registerEntityRenderer(ModEntities.MARKER_A.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.MARKER_B.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.MARKER_C.get(), NoopRenderer::new);
    }
}