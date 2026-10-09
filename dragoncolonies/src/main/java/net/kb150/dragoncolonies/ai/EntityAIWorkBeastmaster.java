package net.kb150.dragoncolonies.ai;

import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import net.kb150.dragoncolonies.DragonColonies;
import net.kb150.dragoncolonies.buildings.BuildingDragonRoost;
import net.kb150.dragoncolonies.buildings.modules.DragonStorageModule;
import net.kb150.dragoncolonies.jobs.JobBeastmaster;
import net.kb150.dragoncolonies.util.DragonStatusHelper;
import net.magister.bookofdragons.entity.data.DragonType;
import net.magister.bookofdragons.entity.stats.SpeciesStatRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class EntityAIWorkBeastmaster extends AbstractEntityAIBasic<JobBeastmaster, BuildingDragonRoost> {

    private enum BeastmasterTask {
        NONE,
        HEALING_DRAGON,
        FEEDING_DRAGON,
        SADDLING_DRAGON,
        BREEDING_DRAGONS
    }

    private BeastmasterTask currentTask = BeastmasterTask.NONE;
    private int cooldown = 0;

    private UUID breedingParent1 = null;
    private UUID breedingParent2 = null;

    public EntityAIWorkBeastmaster(@NotNull JobBeastmaster job) {
        super(job);
        
        // Prüft alle 10 MC-Ticks (0.5 Sekunden)
        this.registerTargets(new AITarget(AIWorkerState.IDLE, this::processWork, 10));
        this.registerTargets(new AITarget(AIWorkerState.START_WORKING, this::processWork, 10));
        this.registerTargets(new AITarget(AIWorkerState.GATHERING_REQUIRED_MATERIALS, this::processWork, 10));
        this.registerTargets(new AITarget(AIWorkerState.DECIDE, this::processWork, 10));
    }

    @Override
    public Class<BuildingDragonRoost> getExpectedBuildingClass() {
        return BuildingDragonRoost.class;
    }

    private IAIState processWork() {
        if (this.building == null || this.worker == null || this.worker.isDeadOrDying()) {
            return AIWorkerState.IDLE;
        }

        // Taktbremse verarbeitet reine Systemverzögerungen ohne künstliche Multiplikatoren
        if (cooldown > 0) {
            cooldown--;
            this.walkToWorkPos(this.building.getPosition()); 
            return null; 
        }

        DragonStorageModule storageModule = this.building.getStorageModule();
        if (storageModule == null) return AIWorkerState.IDLE;

        // Scans nur starten, wenn aktuell kein Task bearbeitet wird
        if (currentTask == BeastmasterTask.NONE) {
            if (scanForInjuredDragons(storageModule, true)) return AIWorkerState.GATHERING_REQUIRED_MATERIALS;
            if (scanForHungryDragons(storageModule)) return AIWorkerState.GATHERING_REQUIRED_MATERIALS;
            if (scanForInjuredDragons(storageModule, false)) return AIWorkerState.GATHERING_REQUIRED_MATERIALS;
            if (scanForUnsaddledDragons(storageModule)) return AIWorkerState.GATHERING_REQUIRED_MATERIALS;
            if (scanForBreedingPairs(storageModule)) return AIWorkerState.GATHERING_REQUIRED_MATERIALS;
        }

        // Führt gesetzte Tasks im selben Durchlauf aus, um Fallthrough-Sperren im Leerlauf zu vermeiden
        switch (currentTask) {
            case HEALING_DRAGON -> { return processHealingTask(storageModule); }
            case FEEDING_DRAGON -> { return processFeedingTask(storageModule); }
            case SADDLING_DRAGON -> { return processSaddlingTask(storageModule); }
            case BREEDING_DRAGONS -> { return processBreedingTask(storageModule); }
            default -> {
                // 2 AI-Ticks Pause (ca. 1 Sekunde) im echten Leerlauf
                cooldown = 2;
                return null;
            }
        }
    }

    private boolean scanForHungryDragons(DragonStorageModule storageModule) {
        for (CompoundTag dragonTag : storageModule.getAllDragons()) {
            if (dragonTag.getBoolean("Deployed") || dragonTag.getBoolean("IsDead") || dragonTag.getBoolean("DragonColonies_IsEgg")) continue;

            if (DragonStatusHelper.isHungry(dragonTag)) {
                DragonType type = parseDragonType(dragonTag);
                List<Item> validFoods = getValidFoodsForDragon(type);
                if (validFoods.isEmpty()) continue;

                // Prüfen, ob irgendein passendes Futter bereits im Inventar getragen wird
                int existingSlot = InventoryUtils.findFirstSlotInItemHandlerWith(
                    this.worker.getInventoryCitizen(), 
                    stack -> validFoods.contains(stack.getItem())
                );

                this.currentTask = BeastmasterTask.FEEDING_DRAGON;

                if (existingSlot != -1) {
                    DragonColonies.debug("AI", "[BEASTMASTER] Futter für %s in Slot %d gefunden. Starte direkte Fütterung.", type, existingSlot);
                    return false; // Kein Materialtransport aus dem Lager erforderlich
                }

                // Erst wenn gar kein Futter getragen wird, Lieferauftrag beim Lager erstellen
                ItemStack foodToRequest = new ItemStack(validFoods.get(0), 5);
                boolean hasItemOrTransferred = this.checkIfRequestForItemExistOrCreateAsync(foodToRequest, 5, 1);

                if (hasItemOrTransferred) {
                    return false;
                } else {
                    this.needsCurrently = new Tuple<>(stack -> validFoods.contains(stack.getItem()), 1);
                    DragonColonies.debug("AI", "[BEASTMASTER] Futter fehlt für %s. Bestelle %s beim Lager.", type, foodToRequest.getItem());
                    return true;
                }
            }
        }
        return false;
    }

    private IAIState processFeedingTask(DragonStorageModule storageModule) {
        int foodSlot = -1;
        CompoundTag targetDragonTag = null;

        for (CompoundTag dragonTag : storageModule.getStoredDragons()) {
            if (dragonTag.getBoolean("Deployed") || dragonTag.getBoolean("IsDead") || dragonTag.getBoolean("DragonColonies_IsEgg")) continue;

            if (DragonStatusHelper.isHungry(dragonTag)) {
                DragonType type = parseDragonType(dragonTag);
                List<Item> validFoods = getValidFoodsForDragon(type);

                int slot = InventoryUtils.findFirstSlotInItemHandlerWith(
                    this.worker.getInventoryCitizen(), 
                    stack -> validFoods.contains(stack.getItem())
                );

                if (slot != -1) {
                    foodSlot = slot;
                    targetDragonTag = dragonTag;
                    break;
                }
            }
        }

        if (foodSlot != -1 && targetDragonTag != null) {
            this.worker.getInventoryCitizen().extractItem(foodSlot, 1, false);

            // Synchronisiert die Arm-Schwing-Animation für den Server und umstehende Client-Spieler
            this.worker.swing(InteractionHand.MAIN_HAND);

            float hp = DragonStatusHelper.getHealth(targetDragonTag);
            float maxHp = DragonStatusHelper.getMaxHealth(targetDragonTag);
            if (hp < maxHp) {
                float foodRegen = Math.max(15.0f, maxHp * 0.10f);
                targetDragonTag.putFloat("Health", Math.min(maxHp, hp + foodRegen));
            }

            CompoundTag needsTag = targetDragonTag.contains("dragonNeeds") ? targetDragonTag.getCompound("dragonNeeds") : new CompoundTag();
            int currentHunger = DragonStatusHelper.getFoodLevel(targetDragonTag);
            int newHunger = Math.min(100, currentHunger + 25);
            needsTag.putInt("foodLevel", newHunger);
            targetDragonTag.put("dragonNeeds", needsTag);

            storageModule.markDirty();
            this.worker.playSound(net.minecraft.sounds.SoundEvents.GENERIC_EAT, 1.0f, 1.0f);

            DragonColonies.debug("AI", "[BEASTMASTER] Drache gefüttert. Hunger erhöht von %d auf %d/100", currentHunger, newHunger);

            this.currentTask = BeastmasterTask.NONE;
            cooldown = 1;
            return AIWorkerState.IDLE;
        } else {
            DragonColonies.debug("AI", "[BEASTMASTER] Fütterung fehlgeschlagen: Kein passendes Futter-Item im Bürger-Inventar.");
            return scanForHungryDragons(storageModule) ? AIWorkerState.GATHERING_REQUIRED_MATERIALS : AIWorkerState.IDLE;
        }
    }

    private boolean scanForInjuredDragons(DragonStorageModule storageModule, boolean emergencyOnly) {
        for (CompoundTag dragonTag : storageModule.getAllDragons()) {
            if (dragonTag.getBoolean("Deployed") || dragonTag.getBoolean("IsDead") || dragonTag.getBoolean("DragonColonies_IsEgg")) continue;

            float maxHealth = DragonStatusHelper.getMaxHealth(dragonTag);
            float healAmount = calculateHealAmount(maxHealth);

            boolean targetEligible = emergencyOnly 
                ? DragonStatusHelper.isEmergencyInjured(dragonTag) 
                : DragonStatusHelper.isWorthHealing(dragonTag, healAmount);

            if (targetEligible) {
                ItemStack healingHerb = new ItemStack(Items.CORNFLOWER, 2);
                boolean hasItemOrTransferred = this.checkIfRequestForItemExistOrCreateAsync(healingHerb, 2, 1);

                this.currentTask = BeastmasterTask.HEALING_DRAGON;
                if (hasItemOrTransferred) {
                    return false;
                } else {
                    this.needsCurrently = new Tuple<>(stack -> stack.is(Items.CORNFLOWER), 1);
                    return true;
                }
            }
        }
        return false;
    }

    private float calculateHealAmount(float maxHealth) {
        int adaptability = 1;
        if (this.worker != null && this.worker.getCitizenData() != null) {
            adaptability = Math.max(1, this.worker.getCitizenData().getCitizenSkillHandler().getLevel(Skill.Adaptability));
        }
        return ((maxHealth * 0.20f) + 40.0f) * (1.0f + (adaptability * 0.03f));
    }

    private IAIState processHealingTask(DragonStorageModule storageModule) {
        int herbSlot = InventoryUtils.findFirstSlotInItemHandlerWith(
            this.worker.getInventoryCitizen(), 
            stack -> stack.is(Items.CORNFLOWER)
        );

        if (herbSlot != -1) {
            CompoundTag targetDragonTag = null;

            for (CompoundTag dragonTag : storageModule.getStoredDragons()) {
                if (dragonTag.getBoolean("Deployed") || dragonTag.getBoolean("IsDead") || dragonTag.getBoolean("DragonColonies_IsEgg")) continue;
                if (DragonStatusHelper.isEmergencyInjured(dragonTag)) {
                    targetDragonTag = dragonTag;
                    break;
                }
            }

            if (targetDragonTag == null) {
                for (CompoundTag dragonTag : storageModule.getStoredDragons()) {
                    if (dragonTag.getBoolean("Deployed") || dragonTag.getBoolean("IsDead") || dragonTag.getBoolean("DragonColonies_IsEgg")) continue;
                    if (DragonStatusHelper.isWorthHealing(dragonTag, calculateHealAmount(DragonStatusHelper.getMaxHealth(dragonTag)))) {
                        targetDragonTag = dragonTag;
                        break;
                    }
                }
            }

            if (targetDragonTag != null) {
                this.worker.getInventoryCitizen().extractItem(herbSlot, 1, false);

                this.worker.swing(InteractionHand.MAIN_HAND);

                float hp = DragonStatusHelper.getHealth(targetDragonTag);
                float maxHealth = DragonStatusHelper.getMaxHealth(targetDragonTag);
                float healValue = calculateHealAmount(maxHealth);

                targetDragonTag.putFloat("Health", Math.min(maxHealth, hp + healValue));

                storageModule.markDirty();
                this.worker.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);

                DragonColonies.debug("AI", "[BEASTMASTER] Drache geheilt auf %.1f HP", targetDragonTag.getFloat("Health"));

                this.currentTask = BeastmasterTask.NONE;
                cooldown = 1;
                return AIWorkerState.IDLE;
            }
        }
        
        return scanForInjuredDragons(storageModule, false) ? AIWorkerState.GATHERING_REQUIRED_MATERIALS : AIWorkerState.IDLE;
    }

    private boolean scanForUnsaddledDragons(DragonStorageModule storageModule) {
        for (CompoundTag dragonTag : storageModule.getAllDragons()) {
            if (dragonTag.getBoolean("Deployed") || dragonTag.getBoolean("IsDead") || dragonTag.getBoolean("DragonColonies_IsEgg")) continue;

            if (DragonStatusHelper.isAdult(dragonTag) && !DragonStatusHelper.isSaddled(dragonTag)) {
                ItemStack saddleReq = new ItemStack(Items.SADDLE, 1);
                boolean hasItemOrTransferred = this.checkIfRequestForItemExistOrCreateAsync(saddleReq, 1, 1);

                this.currentTask = BeastmasterTask.SADDLING_DRAGON;
                if (hasItemOrTransferred) {
                    return false;
                } else {
                    this.needsCurrently = new Tuple<>(stack -> stack.is(Items.SADDLE), 1);
                    return true;
                }
            }
        }
        return false;
    }

    private IAIState processSaddlingTask(DragonStorageModule storageModule) {
        int saddleSlot = InventoryUtils.findFirstSlotInItemHandlerWith(
            this.worker.getInventoryCitizen(), 
            stack -> stack.is(Items.SADDLE)
        );

        if (saddleSlot != -1) {
            CompoundTag targetDragonTag = null;

            for (CompoundTag dragonTag : storageModule.getStoredDragons()) {
                if (dragonTag.getBoolean("Deployed") || dragonTag.getBoolean("IsDead") || dragonTag.getBoolean("DragonColonies_IsEgg")) continue;

                if (DragonStatusHelper.isAdult(dragonTag) && !DragonStatusHelper.isSaddled(dragonTag)) {
                    targetDragonTag = dragonTag;
                    break;
                }
            }

            if (targetDragonTag != null) {
                this.worker.getInventoryCitizen().extractItem(saddleSlot, 1, false);

                this.worker.swing(InteractionHand.MAIN_HAND);

                ListTag invList = targetDragonTag.contains("Inventory", Tag.TAG_LIST) 
                    ? targetDragonTag.getList("Inventory", Tag.TAG_COMPOUND) 
                    : new ListTag();

                // Vorhandenen Slot 0 leeren und echten Sattel-Eintrag setzen
                for (int i = 0; i < invList.size(); i++) {
                    if (invList.getCompound(i).getByte("Slot") == 0) {
                        invList.remove(i);
                        break;
                    }
                }

                CompoundTag saddleNbt = new CompoundTag();
                saddleNbt.putByte("Slot", (byte) 0);
                saddleNbt.putString("id", "minecraft:saddle");
                saddleNbt.putByte("Count", (byte) 1);
                invList.add(saddleNbt);

                targetDragonTag.put("Inventory", invList);
                targetDragonTag.putBoolean("Saddle", true);
                targetDragonTag.putBoolean("HasSaddle", true);
                targetDragonTag.putBoolean("isSaddled", true);

                storageModule.markDirty();
                this.worker.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER, 1.0f, 1.0f);

                DragonColonies.debug("AI", "[BEASTMASTER] Sattel in NBT Slot 0 eingetragen.");

                this.currentTask = BeastmasterTask.NONE;
                cooldown = 1;
                return AIWorkerState.IDLE;
            }
        }
        
        return scanForUnsaddledDragons(storageModule) ? AIWorkerState.GATHERING_REQUIRED_MATERIALS : AIWorkerState.IDLE;
    }

    private boolean scanForBreedingPairs(DragonStorageModule storageModule) {
        if (!storageModule.canStoreMore()) return false;

        List<CompoundTag> stored = storageModule.getStoredDragons();
        
        for (int i = 0; i < stored.size(); i++) {
            CompoundTag parent1 = stored.get(i);
            if (!isReadyToBreed(parent1)) continue;

            for (int j = i + 1; j < stored.size(); j++) {
                CompoundTag parent2 = stored.get(j);
                if (!isReadyToBreed(parent2)) continue;

                DragonType type1 = parseDragonType(parent1);
                DragonType type2 = parseDragonType(parent2);

                if (type1 != null && type1 == type2) {
                    ItemStack requiredFood = type1.getDragonClass().getBreedingFood();
                    boolean hasItemOrTransferred = this.checkIfRequestForItemExistOrCreateAsync(requiredFood, 1, 1);
                    
                    this.breedingParent1 = getOrAssignRoostID(parent1);
                    this.breedingParent2 = getOrAssignRoostID(parent2);
                    this.currentTask = BeastmasterTask.BREEDING_DRAGONS;

                    if (hasItemOrTransferred) {
                        return false;
                    } else {
                        this.needsCurrently = new Tuple<>(stack -> stack.is(requiredFood.getItem()), 1);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private IAIState processBreedingTask(DragonStorageModule storageModule) {
        if (breedingParent1 == null || breedingParent2 == null) {
            this.currentTask = BeastmasterTask.NONE;
            return AIWorkerState.IDLE;
        }

        Optional<CompoundTag> p1Opt = storageModule.getDragonByRoostId(breedingParent1);
        Optional<CompoundTag> p2Opt = storageModule.getDragonByRoostId(breedingParent2);

        if (p1Opt.isEmpty() || p2Opt.isEmpty() || !storageModule.canStoreMore()) {
            this.currentTask = BeastmasterTask.NONE;
            return AIWorkerState.IDLE;
        }

        CompoundTag parent1 = p1Opt.get();
        CompoundTag parent2 = p2Opt.get();
        DragonType type = parseDragonType(parent1);

        Item requiredItem = type.getDragonClass().getBreedingFood().getItem();
        int slot = InventoryUtils.findFirstSlotInItemHandlerWith(
            this.worker.getInventoryCitizen(), 
            stack -> stack.is(requiredItem)
        );

        if (slot != -1) {
            this.worker.getInventoryCitizen().extractItem(slot, 1, false);

            this.worker.swing(InteractionHand.MAIN_HAND);

            parent1.putInt("DragonColonies_BreedingCooldown", 168000);
            parent2.putInt("DragonColonies_BreedingCooldown", 168000);

            CompoundTag egg = new CompoundTag();
            egg.putUUID("RoostDragonID", UUID.randomUUID());
            egg.putBoolean("DragonColonies_IsEgg", true);
            egg.putInt("DragonColonies_IncubationTicks", 24000);
            egg.putString("DragonType", type.getSerializedName());
            egg.putString("CustomName", "Egg (" + type.getDisplayName() + ")");
            
            egg.putInt("Parent1Variant", parent1.getInt("Variant"));
            egg.putInt("Parent2Variant", parent2.getInt("Variant"));

            if (parent1.contains("Genetics")) egg.put("Parent1Genetics", parent1.getCompound("Genetics"));
            if (parent2.contains("Genetics")) egg.put("Parent2Genetics", parent2.getCompound("Genetics"));

            if (parent1.contains("Owner")) {
                egg.putUUID("Owner", parent1.getUUID("Owner"));
                egg.putBoolean("Tame", true);
                
                CompoundTag affectionMap = new CompoundTag();
                affectionMap.putInt(parent1.getUUID("Owner").toString(), 200); 
                egg.put("AffectionMap", affectionMap);
            }

            storageModule.addDragon(egg);
            this.worker.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

            DragonColonies.debug("AI", "[BEASTMASTER] Zucht ausgeführt. Ei im Hort registriert.");

            this.currentTask = BeastmasterTask.NONE;
            cooldown = 10;
            return AIWorkerState.IDLE;
        } else {
            return scanForBreedingPairs(storageModule) ? AIWorkerState.GATHERING_REQUIRED_MATERIALS : AIWorkerState.IDLE;
        }
    }

    private boolean isReadyToBreed(CompoundTag tag) {
        if (!DragonStorageModule.MODE_BREEDING.equals(tag.getString(DragonStorageModule.TAG_ASSIGNMENT_MODE))) {
            return false;
        }

        if (tag.getBoolean("Deployed") || tag.getBoolean("IsDead") || tag.getBoolean("DragonColonies_IsEgg")) return false;
        if (tag.getInt("DragonColonies_BreedingCooldown") > 0) return false;
        if (tag.contains("GrowthStage") && tag.getInt("GrowthStage") < 2) return false;

        if (tag.contains("dragonNeeds")) {
            if (tag.getCompound("dragonNeeds").getInt("foodLevel") < 80) return false;
        } else return false;

        UUID ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        if (ownerId == null) return false;
        if (tag.contains("AffectionMap")) {
            CompoundTag affectionMap = tag.getCompound("AffectionMap");
            if (!affectionMap.contains(ownerId.toString())) return false;
            if (affectionMap.getInt(ownerId.toString()) < 900) return false;
        } else return false;

        return true;
    }

    private UUID getOrAssignRoostID(CompoundTag tag) {
        if (!tag.hasUUID(DragonStorageModule.TAG_ROOST_DRAGON_ID)) {
            tag.putUUID(DragonStorageModule.TAG_ROOST_DRAGON_ID, UUID.randomUUID());
        }
        return tag.getUUID(DragonStorageModule.TAG_ROOST_DRAGON_ID);
    }

    // Liest sowohl Lieblingsfutter als auch Allgemeinfutter aus der BoD-Profil-Registry
	private List<Item> getValidFoodsForDragon(DragonType type) {
		List<Item> foods = new ArrayList<>();
		if (type != null) {
			var profile = SpeciesStatRegistry.getProfile(type.getSerializedName());
			if (profile != null) {
				for (var loc : profile.favoriteFoods()) addFoodIfValid(foods, loc);
				for (var loc : profile.generalFoods()) addFoodIfValid(foods, loc);
			}
		}
		return foods.isEmpty() ? List.of(Items.COD) : foods;
	}
	
	private void addFoodIfValid(List<Item> list, net.minecraft.resources.ResourceLocation loc) {
		Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(loc);
		if (item != null && !list.contains(item)) {
			list.add(item);
		}
	}
	
    private DragonType parseDragonType(CompoundTag tag) {
        if (tag == null) return null;
        String entityIdStr = tag.contains("id") ? tag.getString("id") : tag.getString("DragonType");
        if (!entityIdStr.contains(":")) {
            entityIdStr = "bookofdragons:" + entityIdStr.toLowerCase(Locale.ROOT);
        }
        net.minecraft.resources.ResourceLocation entityLoc = new net.minecraft.resources.ResourceLocation(entityIdStr);
        for (DragonType dt : DragonType.values()) {
            if (dt.getEntityType() != null) {
                net.minecraft.resources.ResourceLocation dtLoc = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(dt.getEntityType());
                if (dtLoc != null && dtLoc.equals(entityLoc)) return dt;
            }
        }
        return DragonType.fromString(entityLoc.getPath());
    }
}