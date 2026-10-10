package net.kb150.woerp.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.kb150.woerp.mixin.PostChainAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;

@Mod.EventBusSubscriber(modid = "woerp", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientRadiationRenderer {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;

        float envRadiation = ClientRadiationData.getDose();
        if (envRadiation <= 0.001f) return;

        Minecraft mc = Minecraft.getInstance();
        PostChain chain = mc.gameRenderer.currentEffect();
        if (chain == null) return;

        // Transformationstabelle zur Projektion von 3D-Weltpunkten auf 2D-Bildschirm-UV
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        Matrix4f viewProj = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix());

        List<BlockPos> emitters = ClientRadiationData.getEmitters();
        float[] screenEmitters = new float[8]; // Max 4 Emitter als UV-Pairs (x, y)
        int validCount = 0;

        for (BlockPos pos : emitters) {
            if (validCount >= 4) break;

            // Kamera-Relativkoordinaten berechnen
            Vector4f clip = new Vector4f(
                (float) (pos.getX() + 0.5 - camPos.x),
                (float) (pos.getY() + 0.5 - camPos.y),
                (float) (pos.getZ() + 0.5 - camPos.z),
                1.0f
            ).mul(viewProj);

            // Verhindert Spiegelung von Emittern hinter dem Rücken des Spielers
            if (clip.w > 0.0f) {
                float uvX = (clip.x / clip.w) * 0.5f + 0.5f;
                float uvY = (clip.y / clip.w) * 0.5f + 0.5f;
                
                screenEmitters[validCount * 2] = uvX;
                screenEmitters[validCount * 2 + 1] = 1.0f - uvY; // GLSL Y-Invertierung
                validCount++;
            }
        }

        final int finalCount = validCount;
        float time = (mc.player != null ? mc.player.tickCount : 0) * 0.05f;

        ((PostChainAccessor) chain).getPasses().forEach(pass -> {
            ShaderInstance shader = pass.getEffect();
            if (shader.getUniform("RadiationDose") != null) shader.getUniform("RadiationDose").set(Math.min(1.0f, envRadiation / 100.0f));
            if (shader.getUniform("Time") != null) shader.getUniform("Time").set(time);
            if (shader.getUniform("EmitterCount") != null) shader.getUniform("EmitterCount").set(finalCount);
            if (shader.getUniform("EmitterUVs") != null) shader.getUniform("EmitterUVs").set(screenEmitters);
        });
    }
}