package net.kb150.dragoncolonies.util;

import net.kb150.dragoncolonies.DragonColonies;
import net.kb150.dragoncolonies.buildings.modules.DragonStorageModule;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * Zentrale Single Source of Truth fuer den Status von Drachen.
 * Bewertet sowohl NBT-Daten aus dem Hort als auch Live-Entitaeten in der Welt
 * nach identischen, unbestechlichen Regeln.
 */
public final class DragonStatusHelper {

    // Schwellenwerte fuer Hunger
    public static final int HUNGER_THRESHOLD_FIT = 60;
    public static final int HUNGER_THRESHOLD_FEEDING = 80;

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

        // Ernährungszustand pruefen
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

        if (getFoodLevel(dragon) < HUNGER_THRESHOLD_FIT) {
            DragonColonies.debug("AI", "[FIT-CHECK-LIVE] Drache {} hungrig ({}/100) -> Rueckruf",
                    dragon.getName().getString(), getFoodLevel(dragon));
            return false;
        }

        if (isEmergencyInjured(dragon)) {
            DragonColonies.debug("AI", "[FIT-CHECK-LIVE] Drache {} akut verletzt ({}/{} HP) -> Rueckruf",
                    dragon.getName().getString(), dragon.getHealth(), dragon.getMaxHealth());
            return false;
        }

        if (!isSaddled(dragon)) {
            DragonColonies.debug("AI", "[FIT-CHECK-LIVE] Drache {} hat keinen Sattel im Slot 0 -> Rueckruf",
                    dragon.getName().getString());
            return false;
        }

        return true;
    }

    public static int getFoodLevel(CompoundTag tag) {
        if (tag == null || !tag.contains("dragonNeeds")) return 100;
        CompoundTag needs = tag.getCompound("dragonNeeds");
        return needs.contains("foodLevel") ? needs.getInt("foodLevel") : 100;
    }

    public static int getFoodLevel(DragonBase dragon) {
        if (dragon == null) return 100;
        if (dragon.getNeedsSystem() != null) {
            return dragon.getNeedsSystem().getFoodLevel();
        }
        return dragon.getEntityData().get(DragonBase.getHungerLevelData());
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
     * Wirtschaftlichkeitspruefung: Verhindert Verschwendung von Heilkraeutern
     * fuer Mini-Kratzer. Eine Heilung lohnt sich, wenn der fehlende Betrag
     * mindestens 65% der moeglichen Heilwirkung ausnutzt oder ein akuter Notfall vorliegt.
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
     * Verhindert Desyncs durch rein ephemere Speicher-Flags.
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

        return dragon.isSaddled();
    }
}