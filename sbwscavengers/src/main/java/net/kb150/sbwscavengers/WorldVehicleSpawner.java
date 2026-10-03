package net.kb150.sbwscavengers;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = "sbwscavengers", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WorldVehicleSpawner {

    public static final Logger LOGGER = LogManager.getLogger("sbwscavengers");

    private static class VehicleData {
        public final String typeId;
        public final List<String> ammoIds;

        public VehicleData(String typeId, List<String> ammoIds) {
            this.typeId = typeId;
            this.ammoIds = ammoIds;
        }
    }

    public static class MarkerProfile {
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> containerWeights;
        public final ForgeConfigSpec.DoubleValue wreckChance;
        public final ForgeConfigSpec.DoubleValue intactHpMin;
        public final ForgeConfigSpec.DoubleValue intactHpMax;
        public final ForgeConfigSpec.DoubleValue intactFuelMin;
        public final ForgeConfigSpec.DoubleValue intactFuelMax;
        public final ForgeConfigSpec.IntValue ammoMin;
        public final ForgeConfigSpec.IntValue ammoMax;
        
        public final List<VehicleData> cachedPool = new ArrayList<>();

        public MarkerProfile(ForgeConfigSpec.Builder builder, String profileName, List<String> defaultWeights, double defaultWreckChance, int defaultAmmoMax) {
            builder.push(profileName);
            
            containerWeights = builder.comment("Gewichtung der Container (z.B. 'land_vehicles;100').")
                    .defineList("container_weights", defaultWeights, obj -> obj instanceof String);
            
            wreckChance = builder.comment("Chance auf Wrack (0.0 bis 1.0)")
                    .defineInRange("wreck_chance", defaultWreckChance, 0.0, 1.0);
            
            intactHpMin = builder.defineInRange("intact_hp_min", 0.40, 0.0, 1.0);
            intactHpMax = builder.defineInRange("intact_hp_max", 0.90, 0.0, 1.0);
            
            intactFuelMin = builder.defineInRange("intact_fuel_min", 0.05, 0.0, 1.0);
            intactFuelMax = builder.defineInRange("intact_fuel_max", 0.30, 0.0, 1.0);
            
            ammoMin = builder.defineInRange("ammo_amount_min", 0, 0, 64);
            ammoMax = builder.defineInRange("ammo_amount_max", defaultAmmoMax, 0, 64);
            
            builder.pop();
        }
    }

    public static final Map<String, MarkerProfile> PROFILES = new HashMap<>();
    public static final ForgeConfigSpec SERVER_CONFIG;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        
        PROFILES.put("marker_a", new MarkerProfile(builder, "Marker_A_Default", 
                List.of("mobile_vehicles;100", "land_vehicles;100", "aircraft;5", "turrets;10"), 0.3, 30));
        
        PROFILES.put("marker_b", new MarkerProfile(builder, "Marker_B_Wrecks", 
                List.of("mobile_vehicles;100", "land_vehicles;100"), 1.0, 0));
                
        PROFILES.put("marker_c", new MarkerProfile(builder, "Marker_C_Airfield", 
                List.of("aircraft;100"), 0.05, 64));

        SERVER_CONFIG = builder.build();
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimplePreparableReloadListener<Map<String, List<VehicleData>>>() {
            @Override
            protected Map<String, List<VehicleData>> prepare(ResourceManager manager, ProfilerFiller profiler) {
                // 1. Fahrzeuge aus 'sbw/vehicles' laden & Munition extrahieren
                Map<String, List<String>> globalVehicleAmmoMap = new HashMap<>();
                Map<ResourceLocation, net.minecraft.server.packs.resources.Resource> vehicleResources = manager.listResources("sbw/vehicles", path -> path.getPath().endsWith(".json"));
                for (Map.Entry<ResourceLocation, net.minecraft.server.packs.resources.Resource> entry : vehicleResources.entrySet()) {
                    try (Reader reader = new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8)) {
                        JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                        if (obj == null) continue;

                        String vehicleId = entry.getKey().getPath().replace("sbw/vehicles/", "").replace(".json", "");
                        List<String> ammoList = new ArrayList<>();
                        extractAmmoFromJson(obj, ammoList);
                        globalVehicleAmmoMap.put(vehicleId, ammoList);
                    } catch (Exception e) {
                        LOGGER.error("Fehler beim Lesen des Fahrzeugs {}", entry.getKey(), e);
                    }
                }

                // 2. Für JEDES Profil den eigenen Pool bauen
                Map<String, List<VehicleData>> newPools = new HashMap<>();
                Map<ResourceLocation, net.minecraft.server.packs.resources.Resource> containerResources = manager.listResources("sbw/containers", path -> path.getPath().endsWith(".json"));

                for (Map.Entry<String, MarkerProfile> profileEntry : PROFILES.entrySet()) {
                    String markerName = profileEntry.getKey();
                    MarkerProfile profile = profileEntry.getValue();
                    List<VehicleData> profilePool = new ArrayList<>();
                    
                    Map<String, Integer> allowedContainers = new HashMap<>();
                    for (String entry : profile.containerWeights.get()) {
                        String[] parts = entry.split(";");
                        if (parts.length >= 1) {
                            String name = parts[0].trim();
                            int weight = (parts.length == 2) ? Integer.parseInt(parts[1].trim()) : 100;
                            allowedContainers.put(name, weight);
                        }
                    }

                    for (Map.Entry<ResourceLocation, net.minecraft.server.packs.resources.Resource> entry : containerResources.entrySet()) {
                        String path = entry.getKey().getPath();
                        String containerName = path.substring(path.lastIndexOf('/') + 1).replace(".json", "");

                        if (!allowedContainers.containsKey(containerName)) continue;
                        int containerWeight = allowedContainers.get(containerName);

                        try (Reader reader = new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8)) {
                            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                            if (obj != null && obj.has("List")) {
                                obj.getAsJsonArray("List").forEach(element -> {
                                    JsonObject itemObj = element.getAsJsonObject();
                                    if (!itemObj.has("Type")) return;

                                    String typeId = itemObj.get("Type").getAsString();
                                    int itemWeight = itemObj.has("Weight") ? itemObj.get("Weight").getAsInt() : 1;
                                    int totalWeight = itemWeight * containerWeight;

                                    String shortId = typeId.contains(":") ? typeId.split(":")[1] : typeId;
                                    List<String> ammoIds = globalVehicleAmmoMap.getOrDefault(shortId, new ArrayList<>());

                                    VehicleData data = new VehicleData(typeId, ammoIds);
                                    for(int i = 0; i < totalWeight; i++) {
                                        profilePool.add(data);
                                    }
                                });
                            }
                        } catch (Exception e) {
                            LOGGER.error("Fehler beim Lesen des Containers {}", entry.getKey(), e);
                        }
                    }
                    newPools.put(markerName, profilePool);
                }
                return newPools;
            }

            @Override
            protected void apply(Map<String, List<VehicleData>> prepared, ResourceManager manager, ProfilerFiller profiler) {
                for (Map.Entry<String, MarkerProfile> entry : PROFILES.entrySet()) {
                    entry.getValue().cachedPool.clear();
                    entry.getValue().cachedPool.addAll(prepared.get(entry.getKey()));
                    LOGGER.info("=== Spawner Profil '{}' bereit: {} Eintraege im Pool ===", entry.getKey(), entry.getValue().cachedPool.size());
                }
            }
        });
    }

    private static void extractAmmoFromJson(JsonObject obj, List<String> ammoList) {
        if (obj.has("AmmoType")) {
            JsonElement ammoTypeElement = obj.get("AmmoType");
            if (ammoTypeElement.isJsonPrimitive()) {
                ammoList.add(ammoTypeElement.getAsString());
            } else if (ammoTypeElement.isJsonArray()) {
                ammoTypeElement.getAsJsonArray().forEach(el -> {
                    if (el.isJsonPrimitive()) {
                        ammoList.add(el.getAsString());
                    } else if (el.isJsonObject() && el.getAsJsonObject().has("Ammo")) {
                        ammoList.add(el.getAsJsonObject().get("Ammo").getAsString());
                    }
                });
            }
        }
        if (obj.has("Ammo")) {
            JsonElement ammoElement = obj.get("Ammo");
            if (ammoElement.isJsonPrimitive()) {
                ammoList.add(ammoElement.getAsString());
            }
        }
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
             if (entry.getValue().isJsonObject()) {
                 extractAmmoFromJson(entry.getValue().getAsJsonObject(), ammoList);
             } else if (entry.getValue().isJsonArray()) {
                 entry.getValue().getAsJsonArray().forEach(el -> {
                     if (el.isJsonObject()) extractAmmoFromJson(el.getAsJsonObject(), ammoList);
                 });
             }
        }
    }

    /**
     * LAGERFEUERGERSCHICHTE: Warum existiert dieses abstruse Mapping?
     * 
     * In den JSON-Dateien von SuperbWarfare steht oft Munition wie "@RifleAmmo". 
     * Das ist kein echtes Item. Der Original-Entwickler hat in der "Ammo.java" folgenden 
     * gigantischen Knoten im Kopf gehabt:
     * 1. Er nimmt das Java-Enum (z.B. "RIFLE").
     * 2. Er macht es komplett klein -> "rifle".
     * 3. Er baut eine wilde for-Schleife mit einem StringBuilder, die den ersten Buchstaben 
     *    wieder groß macht -> "Rifle".
     * 4. Er hängt das Wort "Ammo" hartcodiert hinten dran -> "RifleAmmo".
     * 5. Das wird (mit einem @ davor) in die JSON geschrieben.
     * 
     * Wenn wir das als echtes Item spawnen wollen (superbwarfare:@RifleAmmo), crasht das Spiel.
     * Deswegen lösen wir diesen Wahnsinn hier wieder rückwärts auf in die eigentlichen, 
     * sauberen Registrierungs-IDs (z.B. "superbwarfare:rifle_ammo").
     */
    private static String resolveInternalAmmoEnum(String rawAmmo) {
        if (rawAmmo == null) return "minecraft:air";
        
        if (rawAmmo.startsWith("@")) {
            switch (rawAmmo) {
                case "@HandgunAmmo": return "superbwarfare:handgun_ammo";
                case "@RifleAmmo": return "superbwarfare:rifle_ammo";
                case "@ShotgunAmmo": return "superbwarfare:shotgun_ammo";
                case "@SniperAmmo": return "superbwarfare:sniper_ammo";
                case "@HeavyAmmo": return "superbwarfare:heavy_ammo";
                default:
                    // Fallback: "@SomeAmmo" -> "superbwarfare:some_ammo"
                    String stripped = rawAmmo.substring(1);
                    return "superbwarfare:" + stripped.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase(java.util.Locale.ROOT);
            }
        }
        return rawAmmo;
    }

    public static void transformMarkerToVehicle(ServerLevel level, Entity marker) {
        String markerId = ForgeRegistries.ENTITY_TYPES.getKey(marker.getType()).getPath(); 
        
        MarkerProfile profile = PROFILES.getOrDefault(markerId, PROFILES.get("marker_a"));

        if (profile.cachedPool.isEmpty()) {
            LOGGER.warn("[DEBUG-TRANSFORM] Pool fuer Profil {} ist leer!", markerId);
            return;
        }

        BlockPos pos = marker.blockPosition();
        VehicleData selectedData = profile.cachedPool.get(level.getRandom().nextInt(profile.cachedPool.size()));
        String typeId = selectedData.typeId;

        EntityType<?> selectedType = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(typeId));
        if (selectedType == null) return;

        Entity vehicle = selectedType.create(level);
        if (vehicle != null) {
            float yaw = level.getRandom().nextFloat() * 360.0f;
            vehicle.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0f);
            vehicle.setDeltaMovement(0, 0, 0);

            if (vehicle instanceof com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity superVehicle) {
                
                // FLAG SETZEN: Wir schuetzen unsere Spawner-Fahrzeuge (und Wracks) davor, einfach auszubluten.
                // Forge speichert diese Flag automatisch dauerhaft in der Weltdatei mit ab!

                boolean spawnAsWreck = level.getRandom().nextFloat() < profile.wreckChance.get();

                if (spawnAsWreck) {
					superVehicle.getPersistentData().putBoolean("SBW_PreventBleedout", true);
                    superVehicle.setHealth(-(superVehicle.getMaxHealth() * 0.5f));
                    CompoundTag tag = new CompoundTag();
                    superVehicle.saveWithoutId(tag);
                    tag.putBoolean("Wreck", true);
                    superVehicle.load(tag);
                    
                    setEnergyFromConfig(superVehicle, level, 0.0, 0.05);
                } else {
                    double minHp = profile.intactHpMin.get();
                    double maxHp = profile.intactHpMax.get();
                    float healthRatio = (float) (minHp + level.getRandom().nextFloat() * (maxHp - minHp));
                    superVehicle.setHealth(superVehicle.getMaxHealth() * healthRatio);
                    
                    setEnergyFromConfig(superVehicle, level, profile.intactFuelMin.get(), profile.intactFuelMax.get());
                }

                if (!selectedData.ammoIds.isEmpty()) {
                    String rawAmmoString = selectedData.ammoIds.get(level.getRandom().nextInt(selectedData.ammoIds.size()));
                    
                    // Hier wandeln wir @RifleAmmo etc. in die echten Item-Namen um!
                    String resolvedAmmoString = resolveInternalAmmoEnum(rawAmmoString);
                    
                    ResourceLocation ammoRes = resolvedAmmoString.contains(":") ? new ResourceLocation(resolvedAmmoString) : new ResourceLocation("superbwarfare", resolvedAmmoString);
                    Item ammoItem = ForgeRegistries.ITEMS.getValue(ammoRes);

                    if (ammoItem != null && ammoItem != net.minecraft.world.item.Items.AIR) {
                        int minAmmo = profile.ammoMin.get();
                        int maxAmmo = profile.ammoMax.get();
                        int ammoCount = minAmmo + level.getRandom().nextInt(Math.max(1, maxAmmo - minAmmo + 1));
                        
                        if (ammoCount > 0) {
                            ItemStack ammoStack = new ItemStack(ammoItem, ammoCount);
                            try {
                                superVehicle.setItem(0, ammoStack);
                            } catch (Exception e) {
                                LOGGER.warn("[DEBUG-TRANSFORM] Konnte Munition nicht laden.");
                            }
                        }
                    } else {
                        LOGGER.warn("[DEBUG-TRANSFORM] Konnte Munition nicht finden: {} (Original: {})", resolvedAmmoString, rawAmmoString);
                    }
                }
            }
            level.addFreshEntity(vehicle);
        }
    }
    
    private static void setEnergyFromConfig(com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity superVehicle, ServerLevel level, double min, double max) {
        superVehicle.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY).ifPresent(energy -> {
            int maxEnergy = energy.getMaxEnergyStored();
            energy.extractEnergy(Integer.MAX_VALUE, false);
            float energyRatio = (float) (min + level.getRandom().nextFloat() * (max - min));
            energy.receiveEnergy((int) (maxEnergy * energyRatio), false);
        });
    }
}