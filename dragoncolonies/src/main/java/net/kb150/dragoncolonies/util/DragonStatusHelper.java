package net.kb150.dragoncolonies.util;

import net.kb150.dragoncolonies.DragonColonies;
import net.kb150.dragoncolonies.buildings.modules.DragonStorageModule;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.liberanimalium.chassis.component.simulation.FaunaNeedsSystem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Zentrale Single Source of Truth fuer den Status von Drachen.
 * Bewertet sowohl NBT-Daten aus dem Hort als auch Live-Entitaeten in der Welt
 * nach identischen, unbestechlichen Regeln (jetzt kompatibel mit BoD 1.4 / FAUNA Engine).
 */
public final class DragonStatusHelper {

    // Schwellenwerte fuer Hunger (FAUNA-Skala: 0 - 100)
    public static final int HUNGER_THRESHOLD_FIT = 40;
    public static final int HUNGER_THRESHOLD_FEEDING = 70;

    // Mindest-Gesundheit in Prozent (50%), ab der ein Drache als akut verletzt gilt
    public static final float EMERGENCY_HEALTH_RATIO = 0.50f;

    // Mindest-Schadenspuffer fuer regulaere Behandlungen
    public static final float REGULAR_HEAL_BUFFER = 10.0f;

    private DragonStatusHelper() {}

    /**
     * Reifegrad-Prüfung: Erst ab GrowthStage 2 (Adult) oder 24.000 Ticks (1 MC-Tag)
     * ist das Tier ausgewachsen, sattelbar und einsatzfähig.
     */
    public static boolean isAdult(CompoundTag tag) {
        if (tag == null) return false;
        if (tag.contains("GrowthStage")) return tag.getInt("GrowthStage") >= 2;
        if (tag.contains("AgeTicks")) return tag.getInt("AgeTicks") >= 24000;
        return true;
    }

    public static boolean isAdult(DragonBase dragon) {
        return dragon != null && dragon.getGrowthStage() >= 2;
    }

    /**
     * Bestimmt, ob ein im Hort gespeicherter Drache flugtauglich und einsatzbereit ist.
     * Schliesst Eier, tote Drachen, Jungtiere, Zuchtdrachen, hungrige oder verletzte Tiere aus.
     */
    public static boolean isFitToFly(CompoundTag tag) {
        if (tag == null) return false;

        // Lifecycle-Ausschluesse
        if (tag.getBoolean(DragonStorageModule.TAG_DEPLOYED)) return false;
        if (tag.getBoolean(DragonStorageModule.TAG_IS_DEAD)) return false;
        if (tag.getBoolean("DragonColonies_IsEgg")) return false;

        // Drachen im Zuchtmodus stehen den Wachen nicht fuer Patrouillen zur Verfuegung
        String assignmentMode = tag.getString(DragonStorageModule.TAG_ASSIGNMENT_MODE);
        if (DragonStorageModule.MODE_BREEDING.equals(assignmentMode)) return false;

        if (!isAdult(tag)) return false;

        // Ernährungszustand pruefen (unterstuetzt FaunaNeeds und Legacy-dragonNeeds)
        if (getFoodLevel(tag) < HUNGER_THRESHOLD_FIT) {
            DragonColonies.debug("AI", "[FIT-CHECK] Drache verweigert Flugdienst wegen Hunger: {}/100", getFoodLevel(tag));
            return false;
        }

        // Akut verletzte Tiere (< 50% HP) duerfen nicht starten
        if (isEmergencyInjured(tag)) {
            DragonColonies.debug("AI", "[FIT-CHECK] Drache verweigert Flugdienst wegen akuter Verletzung: {}/{} HP",
                    getHealth(tag), getMaxHealth(tag));
            return false;
        }

        // Pruefung auf Flugunfaehigkeit durch Wunden (FAUNA PartWounds)
        if (tag.contains("WoundGroundedTicks") && tag.getInt("WoundGroundedTicks") > 0) {
            DragonColonies.debug("AI", "[FIT-CHECK] Drache hat Fluegelwunde (WoundGroundedTicks: {})", tag.getInt("WoundGroundedTicks"));
            return false;
        }

        // Physischer Sattel-Check
        if (!isSaddled(tag)) {
            DragonColonies.debug("AI", "[FIT-CHECK] Drache ist nicht gesattelt.");
            return false;
        }

        return true;
    }

    /**
     * Bestimmt, ob ein Drache in der Welt noch fit genug ist, den Wachendienst fortzusetzen.
     */
    public static boolean isFitToFly(DragonBase dragon) {
        if (dragon == null || !dragon.isAlive() || dragon.isRemoved()) return false;

        if (!isAdult(dragon)) return false;

        // 1. FAUNA Schmoll-Check: Entfremdete Drachen verweigern den Dienst komplett
        if (dragon.isEstranged()) {
            DragonColonies.debug("AI", "[FIT-CHECK-LIVE] Drache {} schmollt (isEstranged) -> Rueckruf",
                    dragon.getName().getString());
            return false;
        }

        // 2. FAUNA Flugfaehigkeit: Prueft physische Faehigkeit (Fluegellaehme/Wunde, Incapacitation, Schlaf)
        if (!dragon.canFly()) {
            DragonColonies.debug("AI", "[FIT-CHECK-LIVE] Drache {} kann physisch nicht fliegen (Wunde/Incap/Schlaf) -> Rueckruf",
                    dragon.getName().getString());
            return false;
        }

        // 3. Hunger-Check ueber FAUNA NeedsSystem
        if (getFoodLevel(dragon) < HUNGER_THRESHOLD_FIT) {
            DragonColonies.debug("AI", "[FIT-CHECK-LIVE] Drache {} hungrig ({}/100) -> Rueckruf",
                    dragon.getName().getString(), getFoodLevel(dragon));
            return false;
        }

        // 4. Akute Verletzung
        if (isEmergencyInjured(dragon)) {
            DragonColonies.debug("AI", "[FIT-CHECK-LIVE] Drache {} akut verletzt ({}/{} HP) -> Rueckruf",
                    dragon.getName().getString(), dragon.getHealth(), dragon.getMaxHealth());
            return false;
        }

        // 5. Physischer Sattel vorhanden
        if (!isSaddled(dragon)) {
            DragonColonies.debug("AI", "[FIT-CHECK-LIVE] Drache {} hat keinen Sattel im Slot 0 -> Rueckruf",
                    dragon.getName().getString());
            return false;
        }

        return true;
    }

    public static int getFoodLevel(CompoundTag tag) {
        if (tag == null) return 100;
        // FAUNA speichert unter "FaunaNeeds", aeltere BoD-Versionen unter "dragonNeeds"
        CompoundTag needs = tag.contains("FaunaNeeds") ? tag.getCompound("FaunaNeeds")
                : (tag.contains("dragonNeeds") ? tag.getCompound("dragonNeeds") : null);

        if (needs != null && needs.contains("foodLevel")) {
            return needs.getInt("foodLevel");
        }
        return 100;
    }

    public static int getFoodLevel(DragonBase dragon) {
        if (dragon == null) return 100;
        FaunaNeedsSystem needs = dragon.getNeedsSystem();
        if (needs != null) {
            return needs.getFoodLevel();
        }
        return 100;
    }

    public static boolean isHungry(CompoundTag tag) {
        return getFoodLevel(tag) < HUNGER_THRESHOLD_FEEDING;
    }

    public static boolean isHungry(DragonBase dragon) {
        return getFoodLevel(dragon) < HUNGER_THRESHOLD_FEEDING;
    }

    public static float getHealth(CompoundTag tag) {
        if (tag == null || !tag.contains("Health")) return 20.0f;
        return tag.getFloat("Health");
    }

    public static float getMaxHealth(CompoundTag tag) {
        if (tag == null) return 20.0f;
        if (tag.contains("Attributes", Tag.TAG_LIST)) {
            ListTag attributes = tag.getList("Attributes", Tag.TAG_COMPOUND);
            for (int i = 0; i < attributes.size(); i++) {
                CompoundTag attr = attributes.getCompound(i);
                if ("minecraft:generic.max_health".equals(attr.getString("Name"))) {
                    return (float) attr.getDouble("Base");
                }
            }
        }
        return getHealth(tag);
    }

    /**
     * Akuter Notfall: Drache hat weniger als 50% seiner Maximal-HP.
     */
    public static boolean isEmergencyInjured(CompoundTag tag) {
        float maxHealth = getMaxHealth(tag);
        return getHealth(tag) < (maxHealth * EMERGENCY_HEALTH_RATIO);
    }

    public static boolean isEmergencyInjured(DragonBase dragon) {
        if (dragon == null) return false;
        return dragon.getHealth() < (dragon.getMaxHealth() * EMERGENCY_HEALTH_RATIO);
    }

    /**
     * Regulaere Verletzungspruefung: Fehlt spuerbar Gesundheit (> 10 HP Puffer)?
     */
    public static boolean isInjured(CompoundTag tag) {
        float maxHealth = getMaxHealth(tag);
        return getHealth(tag) < (maxHealth - REGULAR_HEAL_BUFFER);
    }

    public static boolean isInjured(DragonBase dragon) {
        if (dragon == null) return false;
        return dragon.getHealth() < (dragon.getMaxHealth() - REGULAR_HEAL_BUFFER);
    }

    /**
     * Wirtschaftlichkeitspruefung fuer Heiler.
     */
    public static boolean isWorthHealing(CompoundTag tag, float calculatedHealAmount) {
        if (isEmergencyInjured(tag)) return true;

        float maxHp = getMaxHealth(tag);
        float currentHp = getHealth(tag);
        float missingHp = maxHp - currentHp;

        return missingHp >= (calculatedHealAmount * 0.65f);
    }

    /**
     * Prueft, ob in Slot 0 des BoD-Inventars physisch ein Sattel liegt.
     */
    public static boolean isSaddled(CompoundTag tag) {
        if (tag == null) return false;

        if (tag.contains("Inventory", Tag.TAG_LIST)) {
            ListTag invList = tag.getList("Inventory", Tag.TAG_COMPOUND);
            for (int i = 0; i < invList.size(); i++) {
                CompoundTag itemTag = invList.getCompound(i);
                if (itemTag.getByte("Slot") == 0) {
                    String id = itemTag.getString("id");
                    return id.equals("minecraft:saddle") || id.endsWith(":saddle");
                }
            }
        }

        return tag.getBoolean("isSaddled") || tag.getBoolean("IsSaddled") || tag.getBoolean("Saddle");
    }

    public static boolean isSaddled(DragonBase dragon) {
        if (dragon == null) return false;

        // Book of Dragons InventoryComponent: Slot 0 muss physisch belegt sein
        if (dragon.getInventory() != null) {
            ItemStack slot0 = dragon.getInventory().getItem(0);
            return !slot0.isEmpty() && (slot0.is(net.minecraft.world.item.Items.SADDLE) || slot0.getDescriptionId().contains("saddle"));
        }

        return dragon.m_6254_();
    }
}