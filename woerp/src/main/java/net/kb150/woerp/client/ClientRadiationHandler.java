package net.kb150.woerp.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.kb150.woerp.Woerp;
import net.kb150.woerp.mixin.PostChainAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.List;

@Mod.EventBusSubscriber(modid = Woerp.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientRadiationHandler {
    private static final ResourceLocation POST_CHAIN_LOC = new ResourceLocation("minecraft", "shaders/post/woerp_depth_melt.json");
    private static PostChain woerpChain = null;
    private static int lastW = -1, lastH = -1;
    private static boolean isActive = false;

    public static PostChain getWoerpChain() { return woerpChain; }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        float ambient = ClientRadiationData.getAmbient();
        float body = ClientRadiationData.getBodyDose();
        boolean shouldBeActive = ambient > 0.001f || body > 0.001f || !ClientRadiationData.getEmitters().isEmpty();

        if (shouldBeActive && !isActive) {
            isActive = true;
            try {
                int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
                woerpChain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), POST_CHAIN_LOC);
                woerpChain.resize(w, h);
                lastW = w; lastH = h;
                Woerp.LOGGER.info("[WOERP-VFX] Pipeline geladen.");
            } catch (Exception e) {
                Woerp.LOGGER.error("[WOERP-VFX] PostChain Init fehlgeschlagen:", e);
                clearPipeline();
            }
        } else if (!shouldBeActive && isActive) {
            clearPipeline();
        }

        if (isActive && woerpChain != null) {
            int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
            if (w != lastW || h != lastH) {
                woerpChain.resize(w, h);
                lastW = w; lastH = h;
            }
        }
    }

    public static void clearPipeline() {
        if (woerpChain != null) {
            woerpChain.close();
            woerpChain = null;
        }
        isActive = false;
        lastW = lastH = -1;
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || !isActive || woerpChain == null) return;

        RenderTarget maskTarget = ((PostChainAccessor) woerpChain).getCustomRenderTargets().get("emitter_mask");
        if (maskTarget == null) return;

        Minecraft mc = Minecraft.getInstance();

        // Leert die Maske fuer den aktuellen Frame
        maskTarget.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        maskTarget.clear(Minecraft.ON_OSX);

        // Mojangs clear() entbindet den FBO auf Framebuffer 0; Rebind verhindert, dass GameRenderer.renderItemInHand() die First-Person-Hand ins Leere zeichnet
        mc.getMainRenderTarget().bindWrite(true);

        List<BlockPos> emitters = ClientRadiationData.getEmitters();
        if (emitters.isEmpty()) return;

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();

        Matrix4f viewMatrix = new Matrix4f()
            .rotationX((float) Math.toRadians(camera.getXRot()))
            .rotateY((float) Math.toRadians(camera.getYRot() + 180.0f));

        Matrix4f viewProj = new Matrix4f(event.getProjectionMatrix()).mul(viewMatrix);

        maskTarget.bindWrite(true);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_ONE, GL11.GL_ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        Matrix4f ortho = new Matrix4f().setOrtho(0.0f, (float) maskTarget.width, 0.0f, (float) maskTarget.height, -1000.0f, 3000.0f);
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(ortho, VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        boolean hasDrawn = false;

        int segments = 16;
        for (BlockPos pos : emitters) {
            double dx = pos.getX() + 0.5 - camPos.x;
            double dy = pos.getY() + 0.5 - camPos.y;
            double dz = pos.getZ() + 0.5 - camPos.z;

            Vector4f clip = new Vector4f((float) dx, (float) dy, (float) dz, 1.0f).mul(viewProj);
            if (clip.w <= 0.0f) continue;

            float u = (clip.x / clip.w) * 0.5f + 0.5f;
            float v = (clip.y / clip.w) * 0.5f + 0.5f;
            if (u < -0.3f || u > 1.3f || v < -0.3f || v > 1.3f) continue;

            float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            float r = Math.max(0.02f, Math.min(0.40f, 0.45f / Math.max(dist * 0.7f, 0.5f)));
            float intensity = Math.max(0.0f, 1.0f - (dist / 32.0f));

            if (!hasDrawn) {
                builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
                hasDrawn = true;
            }

            float cx = u * maskTarget.width;
            float cy = v * maskTarget.height;
            float rad = r * maskTarget.height;

            for (int i = 0; i < segments; i++) {
                double a1 = i * (2.0 * Math.PI / segments);
                double a2 = (i + 1) * (2.0 * Math.PI / segments);
                builder.vertex(cx, cy, 0.0).color(intensity, r, 0.0f, 1.0f).endVertex();
                builder.vertex(cx + rad * (float) Math.cos(a1), cy + rad * (float) Math.sin(a1), 0.0).color(0.0f, 0.0f, 0.0f, 0.0f).endVertex();
                builder.vertex(cx + rad * (float) Math.cos(a2), cy + rad * (float) Math.sin(a2), 0.0).color(0.0f, 0.0f, 0.0f, 0.0f).endVertex();
            }
        }

        if (hasDrawn) tesselator.end();

        RenderSystem.restoreProjectionMatrix();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        mc.getMainRenderTarget().bindWrite(true);
    }

    @SubscribeEvent
    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        if (!isActive || woerpChain == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        float time = (mc.player != null ? mc.player.tickCount + event.getPartialTick() : 0) * 0.05f;
        float ambient = ClientRadiationData.getAmbient();
        float body = ClientRadiationData.getBodyDose();

        for (var pass : ((PostChainAccessor) woerpChain).getPasses()) {
            EffectInstance effect = pass.getEffect();

            Uniform uDose = effect.getUniform("RadiationDose");
            if (uDose != null) uDose.set(ambient);

            Uniform uBody = effect.getUniform("BodyDose");
            if (uBody != null) uBody.set(body);

            Uniform uTime = effect.getUniform("Time");
            if (uTime != null) uTime.set(time);
        }

        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);
        woerpChain.process(event.getPartialTick());
        mc.getMainRenderTarget().bindWrite(true);

        var baseShader = GameRenderer.getPositionColorShader();
        if (baseShader != null) baseShader.clear();

        for (int i = 3; i >= 0; i--) {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + i);
            RenderSystem.bindTexture(0);
        }

        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }
}