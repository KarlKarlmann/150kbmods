package net.kb150.woerp.client;

public class ClientRadiationData {
    private static float currentDose = 0.0f;

    // Speichert den vom Server diktierten Wert für Geigerzähler-Sound und HUD
    public static void setDose(float dose) { currentDose = dose; }
    public static float getDose() { return currentDose; }
}