package net.kb150.reward_box.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.kb150.reward_box.RewardBox;
import net.kb150.reward_box.client.renderer.RewardChestRenderer;
import net.kb150.reward_box.init.RewardBoxRegistry;
import net.kb150.reward_box.util.RewardBoxConfigManager;

@Mod.EventBusSubscriber(modid = RewardBox.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(RewardBoxRegistry.REWARD_BOX_BE.get(), RewardChestRenderer::new);
    }

    // WICHTIG: Lädt die JSON-Configs auch auf der Client-Seite für Texturen & Renderer
    @SubscribeEvent
    public static void registerClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new RewardBoxConfigManager());
    }
}