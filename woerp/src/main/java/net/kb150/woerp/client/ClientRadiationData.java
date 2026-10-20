package net.kb150.woerp.client;

import net.minecraft.core.BlockPos;
import java.util.Collections;
import java.util.List;

public class ClientRadiationData {
    private static float currentAmbient = 0.0f;
    private static float currentBodyDose = 0.0f;
    private static List<BlockPos> currentEmitters = Collections.emptyList();

    // Snapshot kapselt den Netty-Netzwerk-Thread ab, damit der Render-Loop keine zerrissenen Vektoren liest
    public static void setSnapshot(float ambient, float bodyDose, List<BlockPos> emitters) {
        currentAmbient = ambient;
        currentBodyDose = bodyDose;
        currentEmitters = emitters != null ? emitters : Collections.emptyList();
    }

    public static float getAmbient() { return currentAmbient; }
    public static float getBodyDose() { return currentBodyDose; }
    public static List<BlockPos> getEmitters() { return currentEmitters; }
}