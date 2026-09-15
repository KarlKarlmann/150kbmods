package net.kb150.meteorshower.compat;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.core.api.internal.SharedApi;

/**
 * Isolierte Klasse für Distant Horizons.
 * Darf NIRGENDWO importiert oder genutzt werden, außer im 
 * abgesicherten isModLoaded-Block!
 */
public class DistantHorizonsWrapper {

    /**
     * Prüft, ob die DH-Warteschlange gerade voll ist (> 100 Chunks).
     */
    public static boolean isOverloaded() {
        try {
            int queuedCount = SharedApi.WORLD_CHUNK_UPDATE_MANAGER.getTotalQueuedCount();
            return queuedCount > 100;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Pausiert oder entpausiert die Distant Horizons LOD-Generierung.
     */
    public static void setReadOnly(boolean readOnly) {
        try {
            if (DhApi.Delayed.worldProxy != null) {
                DhApi.Delayed.worldProxy.setReadOnly(readOnly);
            }
        } catch (Throwable ignored) {
        }
    }
}