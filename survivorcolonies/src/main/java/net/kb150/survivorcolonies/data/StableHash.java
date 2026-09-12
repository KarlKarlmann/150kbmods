package net.kb150.survivorcolonies.data;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class StableHash {
    
    // FNV-1a 32-bit Konstanten
    private static final int FNV_PRIME = 0x01000193;
    private static final int FNV_OFFSET_BASIS = 0x811C9DC5;

    /**
     * Wählt deterministisch eine Variante aus einer Liste.
     * @param count Anzahl der verfügbaren Text-Varianten
     * @param uuid UUID des Survivors
     * @param comboKey Der String-Key (z.B. "grumpy|lost_family|...")
     * @param iteration Ein Zähler (z.B. "loop_0", "loop_1"), damit Rückkehr in den Knoten andere Texte triggern kann
     */
    public static int variantPick(int count, UUID uuid, String comboKey, String iteration) {
        if (count <= 0) return 0;
        if (count == 1) return 0;

        String input = uuid.toString() + "|" + comboKey + "|" + iteration;
        int hash = FNV_OFFSET_BASIS;
        
        for (byte b : input.getBytes(StandardCharsets.UTF_8)) {
            hash ^= b;
            hash *= FNV_PRIME;
        }
        
        return Math.abs(hash % count);
    }
}