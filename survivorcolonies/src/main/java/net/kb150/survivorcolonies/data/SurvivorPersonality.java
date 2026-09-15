package net.kb150.survivorcolonies.data;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.UUID;

/**
 * Verwaltet deterministische Persönlichkeitsmerkmale, dynamische Spielwelt-Platzhalter
 * sowie lokalisierbare Begrüßungen und Vertrauensanzeigen.
 */
public class SurvivorPersonality {

    // 1. PERSÖNLICHKEITS-POOLS
    private static final String[] TONES = {
        "grumpy", "panicked", "arrogant", "cheerful", "cynical", "mysterious"
    };

    private static final String[] BACKSTORIES = {
        "mine_collapse", "bandit_raider", "monster_ambush", "exile", "lost_caravan"
    };

    private static final String[] MOTIVATIONS = {
        "safety", "money", "purpose", "revenge", "food"
    };

    // 2. EIGENSCHAFTEN-GETTER (Deterministisch aus UUID)

    /** Wie der Survivor spricht (z. B. "grumpy", "panicked") */
    public static String getTone(UUID uuid) {
        return TONES[getHashIndex(uuid.getLeastSignificantBits(), 0, TONES.length)];
    }

    /** Was dem Survivor zugestoßen ist (z. B. "mine_collapse", "bandit_raider") */
    public static String getBackstory(UUID uuid) {
        return BACKSTORIES[getHashIndex(uuid.getLeastSignificantBits(), 8, BACKSTORIES.length)];
    }

    /** Was den Survivor antreibt (z. B. "safety", "money") */
    public static String getMotivation(UUID uuid) {
        return MOTIVATIONS[getHashIndex(uuid.getMostSignificantBits(), 0, MOTIVATIONS.length)];
    }

    // 3. DYNAMISCHE KONTEXT-GENERATOREN (Monster & Biome aus Registries)

    /** Wählt deterministisch ein Monster aus allen installierten Mods */
    public static String getDynamicMonster(UUID uuid) {
        List<EntityType<?>> monsters = ForgeRegistries.ENTITY_TYPES.getValues().stream()
                .filter(type -> type.getCategory() == MobCategory.MONSTER)
                .toList();

        if (monsters.isEmpty()) return "Monsters";

        int index = getHashIndex(uuid.getLeastSignificantBits(), 16, monsters.size());
        EntityType<?> chosenMonster = monsters.get(index);

        return Component.translatable(chosenMonster.getDescriptionId()).getString();
    }

    /** Generiert einen Ortsnamen aus echten Spiel-Biomen oder prozeduralen Bausteinen */
    public static String getDynamicLocation(UUID uuid, Level level) {
        var biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
        List<String> biomeNames = biomeRegistry.stream()
                .map(biome -> Component.translatable(biomeRegistry.getKey(biome).toLanguageKey("biome")).getString())
                .toList();

        String[] prefixes = {"The Sunken", "The Forgotten", "Mount", "The Ruined", "Fort", "Whispering", "The Burning", "Old"};
        String[] suffixes = {"Valley", "Keep", "Mines", "Outpost", "Shattered Peaks", "Hollow", "Gorge", "Sanctuary"};

        int hash = (int) (uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits());
        int choice = Math.abs(hash % 3);

        if (choice == 0 && !biomeNames.isEmpty()) {
            int biomeIdx = getHashIndex(uuid.getMostSignificantBits(), 0, biomeNames.size());
            return "the " + biomeNames.get(biomeIdx);
        } else {
            int pIdx = getHashIndex(uuid.getMostSignificantBits(), 8, prefixes.length);
            int sIdx = getHashIndex(uuid.getMostSignificantBits(), 16, suffixes.length);
            return prefixes[pIdx] + " " + suffixes[sIdx];
        }
    }

    // 4. DIALOG-WIEDEREINSTIEG & BEZIEHUNGSSTATUS

    /**
     * Liefert eine lokalisierbare, charakter- und vertrauensabhängige Einleitung,
     * wenn ein zuvor begonnenes Gespräch nach einer Pause fortgesetzt wird.
     */
    public static Component getResumePrefix(UUID survivorUuid, int trust, String playerName) {
        String translationKey;
        long bits = survivorUuid.getLeastSignificantBits() ^ survivorUuid.getMostSignificantBits();

        if (trust >= 8) {
            int variant = Math.abs((int) (bits % 3));
            translationKey = "survivorcolonies.dialog.resume.high_trust." + variant;
        } else if (trust <= -5) {
            int variant = Math.abs((int) (bits % 2));
            translationKey = "survivorcolonies.dialog.resume.low_trust." + variant;
        } else {
            String tone = getTone(survivorUuid);
            translationKey = switch (tone) {
                case "grumpy" -> "survivorcolonies.dialog.resume.grumpy." + Math.abs((int) (bits % 2));
                case "panicked" -> "survivorcolonies.dialog.resume.panicked." + Math.abs((int) (bits % 2));
                case "arrogant" -> "survivorcolonies.dialog.resume.arrogant.0";
                case "cheerful" -> "survivorcolonies.dialog.resume.cheerful." + Math.abs((int) (bits % 2));
                case "cynical" -> "survivorcolonies.dialog.resume.cynical.0";
                case "mysterious" -> "survivorcolonies.dialog.resume.mysterious.0";
                default -> "survivorcolonies.dialog.resume.default.0";
            };
        }

        return Component.translatable(translationKey, playerName);
    }

    /**
     * Übersetzbares Label für den aktuellen Vertrauenszustand.
     */
    public static Component getTrustRelationComponent(int trust) {
        if (trust <= -8) {
            return Component.translatable("gui.survivorcolonies.trust.hostile");
        } else if (trust <= -4) {
            return Component.translatable("gui.survivorcolonies.trust.suspicious");
        } else if (trust <= -1) {
            return Component.translatable("gui.survivorcolonies.trust.wary");
        } else if (trust <= 2) {
            return Component.translatable("gui.survivorcolonies.trust.neutral");
        } else if (trust <= 5) {
            return Component.translatable("gui.survivorcolonies.trust.friendly");
        } else if (trust <= 8) {
            return Component.translatable("gui.survivorcolonies.trust.trusted");
        } else {
            return Component.translatable("gui.survivorcolonies.trust.devoted");
        }
    }

    /**
     * Farbwert für die Pergament-Darstellung des Beziehungsstatus.
     */
    public static int getTrustRelationColor(int trust) {
        if (trust <= -4) {
            return 0x8A1C14; // Dunkelrot (Feindselig / Misstrauisch)
        } else if (trust <= -1) {
            return 0x7A4518; // Rostbraun (Vorsichtig)
        } else if (trust <= 2) {
            return 0x5C4632; // Neutrales Pergamentbraun
        } else if (trust <= 6) {
            return 0x245C24; // Sanftes Waldgrün (Freundlich)
        } else {
            return 0x1B6B38; // Satte Vertrauensfarbe (Ergeben / Loyal)
        }
    }

    // 5. HASHING-HELPER
    private static int getHashIndex(long bitSource, int shift, int bound) {
        if (bound <= 0) return 0;
        int hash = Math.abs((int) ((bitSource >> shift) & 0xFFFF));
        return hash % bound;
    }
}