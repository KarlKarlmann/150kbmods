package net.kb150.grubies.client.vfx;

import com.mojang.blaze3d.shaders.Uniform;
import net.kb150.grubies.GrubiesMod;
import net.kb150.grubies.client.ClientTripHandler.OutputChannel;
import net.kb150.grubies.mixin.PostChainAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class ClientShaderManager {
    private static ResourceLocation activePipeline = null;

    @FunctionalInterface
    public interface FloatSupplier { float getAsFloat(); }

    // Isolierte Rohsignale aus der Matrix (werden direkt von den Channel-Settern gefüttert)
    public static float rawTotalAlpha;
    public static float rawDoubleDist, rawDoubleStretch, rawVerticalDist, rawTargetHue, rawHueTolerance;
    public static float rawSaturation, rawHueRotation, rawInvert;
    public static float rawWaveFreq, rawWavePhase, rawColorSafeMode, rawHarmonizeHue, rawHarmonizeAlpha;
    public static float rawStrength, rawDepthCutoff;
    public static float rawBloomThresh, rawBloomIntens;
    public static float rawBlurStrength, rawFocusRadius;

    // Evaluierte Uniform-Zielwerte für GLSL (werden aus den Rohsignalen in evaluate() berechnet)
    public static float totalAlpha = 0f;
    public static float doubleDist = 0f, doubleStretch = 1f, verticalDistance = 0f, targetHue = 0f, hueTolerance = 0f;
    public static float saturation = 0f, hueRotation = 0f, inversion = 0f;
    public static float waveFrequency = 20.0f, wavePhase = 0.0f, colorSafeMode = 0f, harmonizeHue = 0.8f, harmonizeAlpha = 0f;
    public static float strength = 0f, heatStrength = 0f, depthCutoff = 0f;
    public static float bloomThresh = 0f, bloomIntens = 0f;
    public static float blurStrength = 0f, focusRadius = 0.2f;

    // Registriert alle Kanäle für die Matrix und die Mixer-GUI
    public static void registerChannels(List<OutputChannel> list) {
        list.add(new OutputChannel("totalAlpha", v -> rawTotalAlpha = v));

        // 1. Prism Melt & Versatz
        list.add(new OutputChannel("doubleDist", v -> rawDoubleDist = v));
        list.add(new OutputChannel("doubleStretch", v -> rawDoubleStretch = v));
        list.add(new OutputChannel("doubleVertDist", v -> rawVerticalDist = v));
        list.add(new OutputChannel("targetHue", v -> rawTargetHue = v));
        list.add(new OutputChannel("hueTolerance", v -> rawHueTolerance = v));

        // 2. Color Grade
        list.add(new OutputChannel("saturation", v -> rawSaturation = v));
        list.add(new OutputChannel("hueRotation", v -> rawHueRotation = v));
        list.add(new OutputChannel("inversion", v -> rawInvert = v));

        // 3. Depth Harmonize (Tiefenwellen)
        list.add(new OutputChannel("waveFreq", v -> rawWaveFreq = v));
        list.add(new OutputChannel("wavePhase", v -> rawWavePhase = v));
        list.add(new OutputChannel("colorSafeMode", v -> rawColorSafeMode = v));
        list.add(new OutputChannel("harmonizeHue", v -> rawHarmonizeHue = v));
        list.add(new OutputChannel("harmonizeAlpha", v -> rawHarmonizeAlpha = v));
        
        // 4. Heat Distortion
        list.add(new OutputChannel("heatStrength", v -> rawStrength = v));
        list.add(new OutputChannel("depthCutoff", v -> rawDepthCutoff = v));

        // 5. Bloom
        list.add(new OutputChannel("bloomThresh", v -> rawBloomThresh = v));
        list.add(new OutputChannel("bloomIntens", v -> rawBloomIntens = v));

        // 6. Radial Blur (Tunnelblick)
        list.add(new OutputChannel("blurStrength", v -> rawBlurStrength = v));
        list.add(new OutputChannel("focusRadius", v -> rawFocusRadius = v));
    }

    private record BoundUniform(Uniform uniform, Field field) {
        void update() {
            try { uniform.set(field.getFloat(null)); } catch (Exception ignored) {}
        }
    }

    private static final List<BoundUniform> ACTIVE_BOUNDS = new ArrayList<>();

    public static void reset() {
        // Rohwerte auf 0 setzen
        rawTotalAlpha = 0f;
        rawDoubleDist = rawDoubleStretch = rawVerticalDist = rawTargetHue = rawHueTolerance = 0f;
        rawSaturation = rawHueRotation = rawInvert = 0f;
        rawWaveFreq = rawWavePhase = rawColorSafeMode = rawHarmonizeHue = rawHarmonizeAlpha = 0f;
        rawStrength = rawDepthCutoff = 0f;
        rawBloomThresh = rawBloomIntens = 0f;
        rawBlurStrength = rawFocusRadius = 0f;
        
        // Uniforms auf Passive/Standardwerte setzen
        totalAlpha = 0f;
        doubleDist = verticalDistance = targetHue = hueTolerance = 0f;
        doubleStretch = 1.0f; // Neutralwert (keine Linsen-Kompression)
        saturation = hueRotation = inversion = 0f;
        waveFrequency = 20.0f; wavePhase = 0f; colorSafeMode = 0f;
        harmonizeHue = 0.8f; harmonizeAlpha = 0f; // 0.8 = Standard Neon-Lila
        strength = heatStrength = depthCutoff = 0f;
        bloomThresh = bloomIntens = 0f;
        blurStrength = 0f; focusRadius = 0.2f;
    }

    public static void evaluate() {
        totalAlpha = a(rawTotalAlpha);

        // 1. Color Grading
        saturation = Mth.clamp(rawSaturation, -1.0f, 1.0f);
        hueRotation = a(rawHueRotation) % 1.0f;
        
        // Harter Schalter für Invertierung (Behebt den Grau-Matsch)
        inversion = a(rawInvert) > 0.5f ? 1.0f : 0.0f;
        
        // 2. Prism Melt (Schiel- & Schmelzversatz)
        doubleDist = a(rawDoubleDist) * 0.1f;
        verticalDistance = a(rawVerticalDist) * 0.1f;

        if (doubleDist > 0f || verticalDistance > 0f) {
            doubleStretch = rawDoubleStretch >= 0f ? l(rawDoubleStretch, 1f, 5f) : l(-rawDoubleStretch, 1f, 0.2f);
        } else {
            doubleStretch = 1.0f; // Neutral halten bei Inaktivität
        }

        targetHue = a(rawTargetHue) % 1.0f;
        hueTolerance = a(rawHueTolerance);

        // 3. Depth Harmonize (Tiefenwellen)
        waveFrequency = rawWaveFreq != 0f ? a(rawWaveFreq) * 50.0f + 5.0f : 25.0f;
        wavePhase = rawWavePhase != 0f ? rawWavePhase * 10.0f : (Minecraft.getInstance().player.tickCount * 0.15f);
        colorSafeMode = a(rawColorSafeMode) > 0.5f ? 1.0f : 0.0f;
        harmonizeHue = rawHarmonizeHue != 0f ? a(rawHarmonizeHue) % 1.0f : 0.8f;
        harmonizeAlpha = a(rawHarmonizeAlpha);
        
        // 4. Heat Distortion
        strength = heatStrength = a(rawStrength) * 0.05f;
        depthCutoff = strength > 0f ? l(a(rawDepthCutoff), 0.05f, 0.25f) : 0f;

        // Wenn es brennt und das Bild sich spaltet, wird der Versatz durch die Hitzewellen leicht gebremst
        if (heatStrength > 0.02f && doubleDist > 0f) {
            doubleDist *= Math.max(0f, 1f - ((heatStrength - 0.02f) / 0.03f));
        }

        // 5. Bloom
        bloomIntens = a(rawBloomIntens) * 2.5f;
        bloomThresh = bloomIntens > 0f ? l(a(rawBloomThresh), 0.85f, 0.4f) : 0f;
        
        if (inversion > 0.5f && bloomIntens > 0f) {
            bloomThresh = Math.min(bloomThresh, 0.35f); // Bloom-Korrektur bei negativen Bildern
        }

        // 6. Radial Blur (Fokus / Tunnelblick)
        blurStrength = a(rawBlurStrength);
        focusRadius = a(rawFocusRadius) * 0.5f; // Bereich von 0.0 bis 0.5 (Bildschirmmitte)
    }

    public static void setPipeline(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) return;
        if (name == null || name.isEmpty() || "off".equalsIgnoreCase(name)) { clearShader(); return; }

        ResourceLocation loc = new ResourceLocation("minecraft", "shaders/post/" + name + ".json");
        try {
            mc.gameRenderer.loadEffect(loc);
            activePipeline = loc;
            reset();
            cachePipelineUniforms();
        } catch (Exception e) {
            GrubiesMod.LOGGER.error("[VFX-ERR] Pipeline-Load fehlgeschlagen: '{}'", loc, e);
            clearShader();
        }
    }

    private static void cachePipelineUniforms() {
        ACTIVE_BOUNDS.clear();
        Minecraft mc = Minecraft.getInstance();
        PostChain chain = mc.gameRenderer.currentEffect();
        if (chain == null) return;

        Field[] fields = ClientShaderManager.class.getDeclaredFields();

        for (var pass : ((PostChainAccessor) chain).getPasses()) {
            EffectInstance shader = pass.getEffect();
            for (Field f : fields) {
                // Wir binden nur statische Floats, Arrays sind hier komplett verbannt
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

        // Ordnet Java-Variablen-Namen den Uniforms in den JSON-Dateien zu
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
        if (mc.gameRenderer == null || activePipeline == null) return;

        for (int i = 0; i < ACTIVE_BOUNDS.size(); i++) ACTIVE_BOUNDS.get(i).update();

        PostChain chain = mc.gameRenderer.currentEffect();
        if (chain != null) {
            float pw = 1f / Math.max(1, mc.getWindow().getWidth()), ph = 1f / Math.max(1, mc.getWindow().getHeight());
            for (var pass : ((PostChainAccessor) chain).getPasses()) {
                Uniform u = pass.getEffect().getUniform("pixelSize");
                if (u != null) u.set(pw, ph);
            }
        }
    }

    public static void clearShader() {
        Minecraft mc = Minecraft.getInstance();
        ACTIVE_BOUNDS.clear();
        if (mc.gameRenderer != null) {
            activePipeline = null;
            mc.gameRenderer.shutdownEffect();
            reset();
        }
    }

    private static float a(float v) { return Math.abs(v); }
    private static float l(float v, float min, float max) { return Mth.lerp(v, min, max); }
}