package net.kb150.grubies.client.vfx;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.kb150.grubies.client.ClientTripHandler.OutputChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;

import java.util.ArrayList;
import java.util.List;

public class ClientMotionBlur {
    private static final int MAX_SAMPLES = 30;
    private static final float SAMPLE_FREQ = 0.5f;
    private static FrameBufferSet buffers;
    private static float lastTicks = 0;
    private static int currentSample = 0;

    public static float rawIntensity = 0.0f;
    public static float intensity = 0.0f, targetHue = 0.0f, hueTolerance = 1.0f;
    public static float minSat = 0.2f, minVal = 0.15f, maxVal = 0.95f;

    public static void registerChannels(List<OutputChannel> list) {
        list.add(new OutputChannel("motionBlur", v -> rawIntensity = v));
    }

    public static void reset() {
        rawIntensity = intensity = 0.0f;
        if (buffers != null) { buffers.close(); buffers = null; }
    }

    public static void evaluate() {
        intensity = Math.abs(rawIntensity) * 17.2f;
    }

    public static void render(GuiGraphics gui) {
        if (intensity <= 0) {
            if (buffers != null) { buffers.close(); buffers = null; }
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        var main = mc.getMainRenderTarget();
        int vW = Math.max(1, main.viewWidth), vH = Math.max(1, main.viewHeight);

        if (buffers != null && buffers.sizeChanged(vW, vH)) { buffers.close(); buffers = null; }
        if (buffers == null) buffers = new FrameBufferSet(vW, vH);

        float ticks = mc.player != null ? mc.player.tickCount + mc.getFrameTime() : 0;
        if (lastTicks > ticks || lastTicks + SAMPLE_FREQ * MAX_SAMPLES < ticks) {
            lastTicks = ticks - SAMPLE_FREQ * MAX_SAMPLES;
        }

        while (lastTicks + SAMPLE_FREQ <= ticks) {
            currentSample = (currentSample + 1) % MAX_SAMPLES;
            buffers.sample(currentSample);
            lastTicks += SAMPLE_FREQ;
        }

        buffers.draw(gui.guiWidth(), gui.guiHeight());
    }

    private static class FrameBufferSet implements AutoCloseable {
        private final int width, height;
        private final List<SampleBuffer> samples = new ArrayList<>();

        public FrameBufferSet(int w, int h) {
            this.width = w; this.height = h;
            for (int i = 0; i < MAX_SAMPLES; i++) samples.add(new SampleBuffer(i, w, h));
        }

        public boolean sizeChanged(int w, int h) { return width != w || height != h; }
        public void sample(int index) { samples.get(index).capture(); }

        public void draw(int guiW, int guiH) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            for (int i = 0; i < MAX_SAMPLES; i++) samples.get((i + currentSample) % MAX_SAMPLES).draw(guiW, guiH);
            RenderSystem.disableBlend();
        }

        @Override public void close() { samples.forEach(SampleBuffer::close); }
    }

    private static class SampleBuffer implements AutoCloseable {
        private final int sampleIndex;
        private final com.mojang.blaze3d.pipeline.RenderTarget target;
        private boolean active = false;

        public SampleBuffer(int index, int w, int h) {
            this.sampleIndex = index;
            this.target = new com.mojang.blaze3d.pipeline.TextureTarget(w, h, true, Minecraft.ON_OSX);
        }

        public void capture() {
            var main = Minecraft.getInstance().getMainRenderTarget();
            GlStateManager._glBindFramebuffer(GlConst.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GlStateManager._glBindFramebuffer(GlConst.GL_DRAW_FRAMEBUFFER, target.frameBufferId);
            GlStateManager._glBlitFrameBuffer(0, 0, main.viewWidth, main.viewHeight, 0, 0, target.width, target.height, GlConst.GL_COLOR_BUFFER_BIT, GlConst.GL_NEAREST);
            GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, main.frameBufferId);
            active = true;
        }

        public void draw(int guiW, int guiH) {
            float alpha = Math.min(1.0f, sampleIndex * 0.002f * intensity);
            if (!active || alpha <= 0.001f) return;

            var shader = ClientShaderRegistry.HUE_BLUR_SHADER;
            if (shader != null && hueTolerance < 0.99f) {
                RenderSystem.setShader(() -> shader);
                setU(shader, "TargetHue", targetHue);
                setU(shader, "HueTolerance", hueTolerance);
                setU(shader, "MinSaturation", minSat);
                setU(shader, "MinValue", minVal);
                setU(shader, "MaxValue", maxVal);
            } else {
                RenderSystem.setShader(GameRenderer::getPositionTexShader);
            }

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
            RenderSystem.setShaderTexture(0, target.getColorTextureId());

            BufferBuilder buf = Tesselator.getInstance().getBuilder();
            buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            buf.vertex(0, guiH, -90).uv(0, 0).endVertex();
            buf.vertex(guiW, guiH, -90).uv(1, 0).endVertex();
            buf.vertex(guiW, 0, -90).uv(1, 1).endVertex();
            buf.vertex(0, 0, -90).uv(0, 1).endVertex();
            Tesselator.getInstance().end();

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }

        private void setU(net.minecraft.client.renderer.ShaderInstance s, String name, float val) {
            if (s.getUniform(name) != null) s.getUniform(name).set(val);
        }

        @Override public void close() { target.destroyBuffers(); }
    }
}