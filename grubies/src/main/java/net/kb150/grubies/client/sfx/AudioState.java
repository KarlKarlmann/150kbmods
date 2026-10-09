package net.kb150.grubies.client.sfx;

public class AudioState {
    public static float pitch = 1.0f;
    public static float muffle = 0.0f;
    public static float wobble = 0.0f;
    public static float masterGain = 1.0f;

    public static void reset() {
        pitch = 1.0f; masterGain = 1.0f;
        muffle = wobble = 0.0f;
    }

    public static float calcPitch(float ticks) {
        return Math.max(0.1f, pitch + (wobble > 0.01f ? (float) Math.sin(ticks * 0.3f) * wobble * 0.12f : 0f));
    }
}