package net.kb150.woerp.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.kb150.woerp.Woerp;
import net.kb150.woerp.mixin.PostChainAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;

@Mod.EventBusSubscriber(modid = Woerp.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientRadiationHandler {

    private static final ResourceLocation POST_CHAIN_LOC = new ResourceLocation("woerp_depth_melt");
    private static boolean isShaderActive = false;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        float dose = ClientRadiationData.getDose();

        // 1. Pipeline-Lifecycle: Lädt Shader nur bei aktiver Strahlung, um FPS-Spitzen zu verhindern
        if (dose > 0.001f) {
            if (!isShaderActive) {
                try {
                    mc.gameRenderer.loadEffect(POST_CHAIN_LOC);
                    isShaderActive = true;
                    Woerp.LOGGER.debug("[WOERP-VFX] Depth-Melt Shader geladen (Dosis: {})", dose);
                } catch (Exception e) {
                    Woerp.LOGGER.error("[WOERP-VFX] Fehler beim Laden der PostChain 'woerp_depth_melt':", e);
                    return;
                }
            }
        } else {
            if (isShaderActive) {
                mc.gameRenderer.shutdownEffect();
                isShaderActive = false;
                Woerp.LOGGER.debug("[WOERP-VFX] Depth-Melt Shader entladen (Saubere Luft).");
            }
            return;
        }

        PostChain chain = mc.gameRenderer.currentEffect();
        if (chain == null) return;

        // 2. Emitter-Projektion (3D World -> 2D Screen-UV)
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        Matrix4f viewProj = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix());

        List<BlockPos> emitters = ClientRadiationData.getEmitters();
        float[] screenEmitters = new float[8];
        int validCount = 0;

        for (BlockPos pos : emitters) {
            if (validCount >= 4) break;
            Vector4f clip = new Vector4f(
                (float) (pos.getX() + 0.5 - camPos.x),
                (float) (pos.getY() + 0.5 - camPos.y),
                (float) (pos.getZ() + 0.5 - camPos.z),
                1.0f
            ).mul(viewProj);

            if (clip.w > 0.0f) {
                screenEmitters[validCount * 2] = (clip.x / clip.w) * 0.5f + 0.5f;
                screenEmitters[validCount * 2 + 1] = 1.0f - ((clip.y / clip.w) * 0.5f + 0.5f);
                validCount++;
            }
        }

        // 3. Füttern der Uniforms in den geladenen Shader-Pass
        float time = (mc.player.tickCount + event.getPartialTick()) * 0.05f;
        int activeEmitters = validCount;

        ((PostChainAccessor) chain).getPasses().forEach(pass -> {
            ShaderInstance shader = pass.getEffect();
            if (shader.getUniform("RadiationDose") != null) shader.getUniform("RadiationDose").set(Math.min(1.0f, dose / 100.0f));
            if (shader.getUniform("Time") != null) shader.getUniform("Time").set(time);
            if (shader.getUniform("EmitterCount") != null) shader.getUniform("EmitterCount").set(activeEmitters);
            if (shader.getUniform("EmitterUVs") != null) shader.getUniform("EmitterUVs").set(screenEmitters);
        });
    }
}