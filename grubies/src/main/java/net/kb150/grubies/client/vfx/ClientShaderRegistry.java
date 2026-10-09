package net.kb150.grubies.client.vfx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.kb150.grubies.GrubiesMod;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientShaderRegistry {
    public static ShaderInstance HUE_BLUR_SHADER;

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            // Lädt 'hue_motion_blur' direkt über Minecrafts eigenen ResourceProvider aus shaders/core/
            event.registerShader(
                new ShaderInstance(event.getResourceProvider(), new ResourceLocation("hue_motion_blur"), DefaultVertexFormat.POSITION_TEX),
                s -> {
                    HUE_BLUR_SHADER = s;
                    GrubiesMod.LOGGER.info("[VFX-RES] Core-Shader 'hue_motion_blur' geladen.");
                }
            );
        } catch (Exception e) {
            GrubiesMod.LOGGER.error("[VFX-ERR] Fehler beim Registrieren des Hue-Blur Shaders", e);
        }
    }
}