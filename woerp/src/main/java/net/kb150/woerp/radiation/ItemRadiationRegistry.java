package net.kb150.woerp.radiation;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ItemRadiationRegistry {
    // Primitives Lookup-Array: Mappt Registry-IDs direkt auf Emissionswerte.
    // Verhindert O(N)-Tag-Scans und String-Vergleiche im Server-Tick bei Hunderten automatisierten Containern.
    private static float[] RAD_LOOKUP = new float[0];

    public static void rebuildCache() {
        int maxId = BuiltInRegistries.ITEM.size();
        RAD_LOOKUP = new float[maxId + 100];

        for (Item item : BuiltInRegistries.ITEM) {
            int id = BuiltInRegistries.ITEM.getId(item);
            float rad = 0.0f;
            ItemStack stack = new ItemStack(item);
            
            for (ItemRadiationLevel level : ItemRadiationLevel.values()) {
                if (stack.is(level.tag)) {
                    rad = level.emissionPerItem;
                    break;
                }
            }
            if (id < RAD_LOOKUP.length) RAD_LOOKUP[id] = rad;
        }
    }

    // Direkter O(1)-Speicherzugriff: Verbraucht auch bei vollen Doppel-Kisten im 20Hz-Tick keine messbaren CPU-Zyklen.
    public static float getStackEmission(ItemStack stack) {
        if (stack.isEmpty()) return 0.0f;
        int id = BuiltInRegistries.ITEM.getId(stack.getItem());
        return (id < RAD_LOOKUP.length) ? RAD_LOOKUP[id] * stack.getCount() : 0.0f;
    }
}