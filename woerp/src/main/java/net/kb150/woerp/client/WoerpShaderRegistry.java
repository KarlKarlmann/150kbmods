package net.kb150.woerp.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.kb150.woerp.Woerp;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Woerp.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class WoerpShaderRegistry {
    public static ShaderInstance MELT_SHADER;

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            // Greift durch den Vanilla-Namen direkt auf assets/minecraft/shaders/program/woerp_depth_melt.json zu
            event.registerShader(
                new ShaderInstance(event.getResourceProvider(), new ResourceLocation("woerp_depth_melt"), DefaultVertexFormat.POSITION_TEX),
                shader -> {
                    MELT_SHADER = shader;
                    Woerp.LOGGER.info("[WOERP-VFX] Core-Shader 'woerp_depth_melt' erfolgreich geladen.");
                }
            );
        } catch (Exception e) {
            Woerp.LOGGER.error("[WOERP-VFX] Fehler beim Laden des Melt-Shaders! Details:", e);
        }
    }
}