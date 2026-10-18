package net.kb150.meteorshower.compat;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import net.kb150.meteorshower.MeteorShower;

public class DistantHorizonsWrapper {

    public static boolean isOverloaded() {
        try {
            return SharedApi.WORLD_CHUNK_UPDATE_MANAGER.getTotalQueuedCount() > 50;
        } catch (Throwable t) {
            return false;
        }
    }

    // Setzt das CPU-Laufzeitverhältnis der DH-Workerthreads temporär auf das Minimum (1%).
    // Verhindert das Abreißen und Neuerstellen der Threadpools, das bei setReadOnly() auftritt.
    public static void setThrottled(boolean throttle) {
        try {
            if (DhApi.Delayed.configs != null) {
                var ratio = DhApi.Delayed.configs.multiThreading().threadRuntimeRatio();
                if (throttle) ratio.setValue(0.01); else ratio.clearValue();
            } else {
                com.seibel.distanthorizons.core.config.Config.Common.MultiThreading.threadRunTimeRatio.setApiValue(throttle ? 0.01 : null);
            }
            MeteorShower.LOGGER.info("[DHCompat] DH-Hintergrundthreads {}", throttle ? "auf 1% gedrosselt." : "wieder freigegeben.");
        } catch (Throwable t) {
            MeteorShower.LOGGER.warn("[DHCompat] Fehler beim Drosseln von Distant Horizons: {}", t.getMessage());
        }
    }
}