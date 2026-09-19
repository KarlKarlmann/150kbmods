package net.kb150.survivorcolonies.entity.ai;

/**
 * Zentraler Tagesablauf-Zustand eines Survivors.
 * Wird von {@link net.kb150.survivorcolonies.entity.SurvivorEntity#updateActivity()}
 * einmal pro Sekunde neu berechnet. Alle Goals fragen nur noch diesen Zustand ab,
 * anstatt selbst Tageszeit/Inventar/Persönlichkeit auszuwerten.
 */
public enum SurvivorActivity {
    /** Höchste Priorität: Kampf oder Flucht. Blockiert alle Alltags-Goals. */
    COMBAT,
    /** Früher Abend: Zelt & Lagerfeuer werden aufgebaut. */
    EVENING_SETUP,
    /** Nachts, wenn der Survivor sich zum Schlafen entscheidet. Blockiert fast alles. */
    SLEEPING,
    /** Nachts, wenn der Survivor stattdessen wach bleibt/jagt. */
    NIGHT_PATROL,
    /** Sitzt aktiv am Lagerfeuer (kochen/heilen/essen). */
    CAMPFIRE_IDLE,
    /** Normaler Tagesablauf: erkunden, scavengen, ggf. Unterschlupf bei Regen. */
    DAY_ROAM
}
