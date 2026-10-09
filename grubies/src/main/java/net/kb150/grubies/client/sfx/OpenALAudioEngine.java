package net.kb150.grubies.client.sfx;

import net.kb150.grubies.GrubiesMod;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;

public class OpenALAudioEngine {
    private static boolean efxSupported = false;
    private static boolean initialized = false;
    private static int lowpassFilterId = 0;

    public static void init() {
        if (initialized) return;
        initialized = true;

        try {
            long device = ALC10.alcGetContextsDevice(ALC10.alcGetCurrentContext());
            efxSupported = ALC10.alcIsExtensionPresent(device, "ALC_EXT_EFX");

            if (efxSupported) {
                // Systemweiter Filter-Slot; verhindert Treiber-Handle-Leaks bei dauerhaften Source-Reallocations in OpenAL
                lowpassFilterId = EXTEfx.alGenFilters();
                EXTEfx.alFilteri(lowpassFilterId, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
                GrubiesMod.LOGGER.info("[SFX] OpenAL EFX Hardware-Filter bereit.");
            }
        } catch (Exception e) {
            GrubiesMod.LOGGER.error("[SFX] OpenAL Init abgebrochen", e);
        }
    }

    public static void applyDirectFilter(int sourceId, float muffleVal) {
        if (!initialized) init();
        if (!efxSupported || lowpassFilterId == 0) return;

        if (muffleVal > 0.01f) {
            // EXTEfx.AL_LOWPASS_GAINHF dämpft hochfrequente Anteile analog zu Unterwasser-/Watte-Filtern
            EXTEfx.alFilterf(lowpassFilterId, EXTEfx.AL_LOWPASS_GAINHF, Math.max(0.02f, 1.0f - muffleVal));
            AL10.alSourcei(sourceId, EXTEfx.AL_DIRECT_FILTER, lowpassFilterId);
        } else {
            // Deaktiviert den Filter im Treiber, um Hardware-Ressourcen bei inaktivem Muffle sofort freizugeben
            AL10.alSourcei(sourceId, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
        }
    }
}