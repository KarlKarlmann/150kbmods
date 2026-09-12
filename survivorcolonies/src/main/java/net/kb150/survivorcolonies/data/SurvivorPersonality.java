package net.kb150.survivorcolonies.data;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.UUID;

public class SurvivorPersonality {

    // 1. PERSÖNLICHKEITS-POOLS (Eigenschaften, aus denen via UUID gewürfelt wird)
    private static final String[] TONES = {
        "grumpy", "panicked", "arrogant", "cheerful", "cynical", "mysterious"
    };
    
    private static final String[] BACKSTORIES = {
        "mine_collapse", "bandit_raid", "monster_ambush", "exile", "lost_caravan"
    };
    
    private static final String[] MOTIVATIONS = {
        "safety", "money", "purpose", "revenge", "food"
    };

    // 2. EIGENSCHAFTEN-GETTER (Deterministisch aus der UUID berechnet)

    /** Wie der Survivor spricht (z. B. "grumpy", "panicked") */
    public static String getTone(UUID uuid) {
        return TONES[getHashIndex(uuid.getLeastSignificantBits(), 0, TONES.length)];
    }

    /** Was dem Survivor zugestoßen ist (z. B. "mine_collapse", "bandit_raid") */
    public static String getBackstory(UUID uuid) {
        return BACKSTORIES[getHashIndex(uuid.getLeastSignificantBits(), 8, BACKSTORIES.length)];
    }

    /** Was der Survivor sucht (z. B. "safety", "money") */
    public static String getMotivation(UUID uuid) {
        return MOTIVATIONS[getHashIndex(uuid.getMostSignificantBits(), 0, MOTIVATIONS.length)];
    }

    // 3. DYNAMISCHE KONTEXT-GENERATOREN (Laden Monster & Biome aus Forge/Mods)

    /** Wählt deterministisch ein Monster aus ALLEN installierten Mods */
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

    // 4. HASHING-HELPER
    private static int getHashIndex(long bitSource, int shift, int bound) {
        if (bound <= 0) return 0;
        int hash = Math.abs((int) ((bitSource >> shift) & 0xFFFF));
        return hash % bound;
    }
}