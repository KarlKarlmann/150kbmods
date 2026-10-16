package net.kb150.grubies.client.vfx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import net.kb150.grubies.GrubiesMod;
import net.kb150.grubies.client.ClientTripHandler.OutputChannel;
import net.kb150.grubies.mixin.PostChainAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientShaderManager {
    private static ResourceLocation activePipeline = null;
    private static PostChain grubChain = null;
    private static int lastW = -1, lastH = -1;

    // Rohsignale aus der Matrix
    public static float rawDoubleDist, rawDoubleStretch, rawVerticalDist, rawTargetHue;
    public static float rawSaturation, rawHueRotation, rawInvert;
    public static float rawWaveFreq, rawWavePhase, rawColorSafeMode, rawHarmonizeHue;
    public static float rawStrength, rawDepthCutoff;
    public static float rawBloomThresh, rawBloomIntens;
    public static float rawRadialBlur;

    // Evaluierte Uniforms für GLSL
    public static float totalAlpha = 0f;
    public static float doubleDist = 0f, doubleStretch = 1f, verticalDistance = 0f, targetHue = 0f, hueTolerance = 0f;
    public static float saturation = 0f, hueRotation = 0f, inversion = 0f;
    public static float waveFrequency = 20.0f, wavePhase = 0.0f, colorSafeMode = 1.0f, harmonizeHue = 0.8f, harmonizeAlpha = 0f;
    public static float strength = 0f, heatStrength = 0f, depthCutoff = 0.15f;
    public static float bloomThresh = 0.6f, bloomIntens = 0f;
    public static float blurStrength = 0f, focusRadius = 0.2f;

    public static PostChain getGrubChain() { return grubChain; }

    public static void registerChannels(List<OutputChannel> list) {
        list.add(new OutputChannel("doubleDist", v -> rawDoubleDist = v));
        list.add(new OutputChannel("doubleVertDist", v -> rawVerticalDist = v));
        list.add(new OutputChannel("doubleStretch", v -> rawDoubleStretch = v));
        list.add(new OutputChannel("targetHue", v -> rawTargetHue = v));

        list.add(new OutputChannel("saturation", v -> rawSaturation = v));
        list.add(new OutputChannel("hueRotation", v -> rawHueRotation = v));
        list.add(new OutputChannel("inversion", v -> rawInvert = v));

        list.add(new OutputChannel("waveFreq", v -> rawWaveFreq = v));
        list.add(new OutputChannel("wavePhase", v -> rawWavePhase = v));
        list.add(new OutputChannel("colorSafeMode", v -> rawColorSafeMode = v));
        list.add(new OutputChannel("harmonizeHue", v -> rawHarmonizeHue = v));
        
        list.add(new OutputChannel("heatStrength", v -> rawStrength = v));
        list.add(new OutputChannel("depthCutoff", v -> rawDepthCutoff = v));

        list.add(new OutputChannel("bloomThresh", v -> rawBloomThresh = v));
        list.add(new OutputChannel("bloomIntens", v -> rawBloomIntens = v));

        list.add(new OutputChannel("radialBlur", v -> rawRadialBlur = v));
    }

    private record BoundUniform(Uniform uniform, Field field) {
        void update() {
            try { uniform.set(field.getFloat(null)); } catch (Exception ignored) {}
        }
    }

    private static final List<BoundUniform> ACTIVE_BOUNDS = new ArrayList<>();

    public static void reset() {
        rawDoubleDist = rawDoubleStretch = rawVerticalDist = rawTargetHue = 0f;
        rawSaturation = rawHueRotation = rawInvert = 0f;
        rawWaveFreq = rawWavePhase = rawColorSafeMode = rawHarmonizeHue = 0f;
        rawStrength = rawDepthCutoff = 0f;
        rawBloomThresh = rawBloomIntens = 0f;
        rawRadialBlur = 0f;
        
        totalAlpha = 0f;
        doubleDist = verticalDistance = targetHue = hueTolerance = 0f; doubleStretch = 1.0f;
        saturation = hueRotation = inversion = 0f;
        waveFrequency = 20.0f; wavePhase = 0f; colorSafeMode = 1.0f;
        harmonizeHue = 0.8f; harmonizeAlpha = 0f;
        strength = heatStrength = 0f; depthCutoff = 0.15f;
        bloomThresh = 0.6f; bloomIntens = 0f;
        blurStrength = 0f; focusRadius = 0.2f;
    }

    private static void ensureSize() {
        if (grubChain == null) return;
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();
        if (w != lastW || h != lastH) {
            grubChain.resize(w, h);
            lastW = w;
            lastH = h;
        }
    }

    public static void evaluate(int activeSeed) {
        int seed = activeSeed != -1 ? activeSeed : 1337;

        float rDist = Math.abs(rawDoubleDist);
        float rVDist = Math.abs(rawVerticalDist);
        float rStretch = Math.abs(rawDoubleStretch);
        float rTargetHue = Math.abs(rawTargetHue);

        if ((rStretch != 0 || rTargetHue != 0) && rDist == 0 && rVDist == 0) {
            float avg = (rStretch + rTargetHue) / 2.0f;
            if (avg == 0f) avg = 0.25f;
            if (seedRandom(seed, 10) > 0.5f) rDist = avg;
            else rVDist = avg;
        }

        doubleDist = rDist * 0.1f;
        verticalDistance = rVDist * 0.1f;
        doubleStretch = rawDoubleStretch != 0 ? (rawDoubleStretch >= 0 ? 1.0f + rawDoubleStretch * 3.0f : 1.0f / (1.0f + Math.abs(rawDoubleStretch) * 3.0f)) : 1.0f;
        targetHue = rTargetHue % 1.0f;
        hueTolerance = targetHue != 0 ? seedFloat(seed, 11, 0.05f, 0.35f) : 0.0f;

        boolean anyHarmonizeModifier = rawWaveFreq != 0 || rawWavePhase != 0 || rawColorSafeMode != 0 || rawHarmonizeHue != 0;
        harmonizeAlpha = anyHarmonizeModifier ? seedFloat(seed, 12, 0.25f, 0.85f) : 0.0f;

        waveFrequency = rawWaveFreq != 0 ? Math.abs(rawWaveFreq) * 50.0f + 5.0f : 25.0f;
        wavePhase = rawWavePhase != 0 ? rawWavePhase * 10.0f : (Minecraft.getInstance().player != null ? Minecraft.getInstance().player.tickCount * 0.15f : 0f);
        colorSafeMode = rawColorSafeMode != 0 ? (Math.abs(rawColorSafeMode) > 0.5f ? 1.0f : 0.0f) : 1.0f;
        harmonizeHue = rawHarmonizeHue != 0 ? Math.abs(rawHarmonizeHue) % 1.0f : 0.8f;

        float rHeat = Math.abs(rawStrength);
        if (rawDepthCutoff != 0 && rHeat == 0) rHeat = seedFloat(seed, 13, 0.15f, 0.60f);
        strength = heatStrength = rHeat * 0.05f;
        depthCutoff = rawDepthCutoff != 0 ? l(Math.abs(rawDepthCutoff), 0.05f, 0.25f) : 0.15f;

        float rBloom = Math.abs(rawBloomIntens);
        if (rawBloomThresh != 0 && rBloom == 0) rBloom = seedFloat(seed, 14, 0.20f, 0.70f);
        bloomIntens = rBloom * 2.5f;
        bloomThresh = rawBloomThresh != 0 ? l(Math.abs(rawBloomThresh), 0.85f, 0.4f) : 0.60f;

        float rBlur = Math.abs(rawRadialBlur);
        blurStrength = rBlur;
        focusRadius = rBlur > 0 ? Mth.clamp(0.45f - rBlur * 0.40f, 0.05f, 0.45f) : 0.20f;

        saturation = Mth.clamp(rawSaturation, -1.0f, 1.0f);
        hueRotation = Math.abs(rawHueRotation) % 1.0f;
        inversion = Math.abs(rawInvert) > 0.5f ? 1.0f : 0.0f;

        boolean passActive = (rDist > 0 || rVDist > 0 || harmonizeAlpha > 0 || heatStrength > 0 || bloomIntens > 0 || blurStrength > 0 || saturation != 0 || hueRotation != 0 || inversion != 0);
        totalAlpha = passActive ? seedFloat(seed, 15, 0.10f, 1.00f) : 0.0f;
    }

    // 1. Sichere Kopie des Welt-Tiefenpuffers DIREKT nach dem Level-Pass (ohne doppelten resize!)
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        if (grubChain == null || totalAlpha <= 0.001f) return;

        ensureSize();

        Minecraft mc = Minecraft.getInstance();
        RenderTarget src = mc.getMainRenderTarget();
        RenderTarget dst = ((PostChainAccessor) grubChain).getCustomRenderTargets().get("worlddepth");
        
        if (dst != null) {
            dst.copyDepthFrom(src);
            src.bindWrite(false);
        }
    }

    // 2. Ausführen der Shader-Pipeline & Bereinigen des Shader-Caches für 3D-Items
    @SubscribeEvent
    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        if (grubChain == null || totalAlpha <= 0.001f) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        ensureSize();

        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);

        tickUniforms();
        
        // Shader auf das Weltbild anwenden
        grubChain.process(event.getPartialTick());

        // FBO wieder binden
        mc.getMainRenderTarget().bindWrite(true);

        // MINECRAFT SHADER-CACHE ZURÜCKSETZEN (Behebt verschwindende Items!)
        var anyShader = GameRenderer.getPositionColorShader();
        if (anyShader != null) {
            anyShader.clear();
        }

        // Texture-Units 3, 2, 1, 0 bereinigen
        for (int i = 3; i >= 0; i--) {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + i);
            RenderSystem.bindTexture(0);
        }

        // Depth-Buffer für flaches HUD-Overlay freigeben
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        // Standard-GL-State wiederherstellen
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static float seedRandom(int seed, int salt) {
        int h = seed ^ (salt * 0x45d9f3b);
        h = ((h >>> 16) ^ h) * 0x45d9f3b;
        h = ((h >>> 16) ^ h) * 0x45d9f3b;
        return (Math.abs((h >>> 16) ^ h) % 10000) / 10000.0f;
    }

    private static float seedFloat(int seed, int salt, float min, float max) {
        return min + seedRandom(seed, salt) * (max - min);
    }

    public static void setPipeline(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) return;
        if (name == null || name.isEmpty() || "off".equalsIgnoreCase(name)) { clearShader(); return; }

        ResourceLocation loc = new ResourceLocation("minecraft", "shaders/post/" + name + ".json");
        try {
            if (grubChain != null) grubChain.close();
            
            grubChain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), loc);
            int w = mc.getWindow().getWidth();
            int h = mc.getWindow().getHeight();
            grubChain.resize(w, h);
            lastW = w;
            lastH = h;
            
            activePipeline = loc;
            reset();
            cachePipelineUniforms();
            GrubiesMod.LOGGER.info("[VFX] Standalone Pipeline '{}' geladen.", loc);
        } catch (Exception e) {
            GrubiesMod.LOGGER.error("[VFX-ERR] Standalone Pipeline-Load fehlgeschlagen: '{}'", loc, e);
            clearShader();
        }
    }

    private static void cachePipelineUniforms() {
        ACTIVE_BOUNDS.clear();
        if (grubChain == null) return;

        Field[] fields = ClientShaderManager.class.getDeclaredFields();

        for (var pass : ((PostChainAccessor) grubChain).getPasses()) {
            EffectInstance shader = pass.getEffect();
            for (Field f : fields) {
                if (f.getType() != float.class || !Modifier.isStatic(f.getModifiers())) continue;
                Uniform u = findUniform(shader, f.getName());
                if (u != null) {
                    f.setAccessible(true);
                    ACTIVE_BOUNDS.add(new BoundUniform(u, f));
                }
            }
        }
    }

    private static Uniform findUniform(EffectInstance shader, String fieldName) {
        Uniform u = shader.getUniform(fieldName);
        if (u != null) return u;

        String alias = switch (fieldName) {
            case "doubleDist" -> "distance";
            case "doubleStretch" -> "stretch";
            case "verticalDistance" -> "verticalDistance";
            case "targetHue" -> "targetHue";
            case "hueTolerance" -> "hueTolerance";
            case "saturation" -> "saturation";
            case "hueRotation" -> "hueRotation";
            case "inversion" -> "inversion";
            case "harmonizeHue" -> "harmonizeHue";
            case "harmonizeAlpha" -> "harmonizeAlpha";
            case "waveFrequency" -> "waveFrequency";
            case "wavePhase" -> "wavePhase";
            case "colorSafeMode" -> "colorSafeMode";
            case "bloomThresh" -> "bloomThreshold";
            case "bloomIntens" -> "bloomIntensity";
            case "blurStrength" -> "blurStrength";
            case "focusRadius" -> "focusRadius";
            default -> null;
        };
        return alias != null ? shader.getUniform(alias) : null;
    }

    public static void tickUniforms() {
        Minecraft mc = Minecraft.getInstance();
        if (grubChain == null) return;

        for (int i = 0; i < ACTIVE_BOUNDS.size(); i++) ACTIVE_BOUNDS.get(i).update();

        float pw = 1f / Math.max(1, mc.getWindow().getWidth()), ph = 1f / Math.max(1, mc.getWindow().getHeight());
        for (var pass : ((PostChainAccessor) grubChain).getPasses()) {
            Uniform u = pass.getEffect().getUniform("pixelSize");
            if (u != null) u.set(pw, ph);
        }
    }

    public static void clearShader() {
        ACTIVE_BOUNDS.clear();
        activePipeline = null;
        lastW = lastH = -1;
        if (grubChain != null) {
            grubChain.close();
            grubChain = null;
        }
        reset();
    }

    private static float l(float v, float min, float max) { return Mth.lerp(v, min, max); }
}