package net.kb150.superbwarfarespawner;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class VehicleLootManager {

    /**
     * Liest alle Items aus der Loot-JSON des Fahrzeugs aus (z. B. data/superbwarfare/sbw/loot/ah_6.json)
     */
    public static List<Item> getVehicleLootPool(ResourceManager resourceManager, String vehicleEntityName) {
        List<Item> lootItems = new ArrayList<>();
        ResourceLocation location = new ResourceLocation("superbwarfare", "sbw/loot/" + vehicleEntityName + ".json");

        try {
            var resource = resourceManager.getResource(location);
            if (resource.isPresent()) {
                try (var reader = new InputStreamReader(resource.get().open())) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    JsonArray pools = json.getAsJsonArray("Pools");

                    for (JsonElement poolElem : pools) {
                        JsonObject pool = poolElem.getAsJsonObject();
                        JsonArray entries = pool.getAsJsonArray("Entries");

                        for (JsonElement entryElem : entries) {
                            JsonObject entry = entryElem.getAsJsonObject();
                            if (entry.has("Name")) {
                                String itemName = entry.get("Name").getAsString();
                                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemName));
                                if (item != null && item != Items.AIR) {
                                    lootItems.add(item);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Konnte Loot-Table für Fahrzeug nicht laden: " + vehicleEntityName);
        }

        // Fallback, falls die Loot-Table leer oder unlesbar war
        if (lootItems.isEmpty()) {
            lootItems.add(Items.IRON_INGOT);
        }

        return lootItems;
    }
}