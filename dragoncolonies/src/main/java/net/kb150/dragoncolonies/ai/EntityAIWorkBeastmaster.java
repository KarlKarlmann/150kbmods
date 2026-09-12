package net.kb150.dragoncolonies.ai;

import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import net.kb150.dragoncolonies.buildings.BuildingDragonRoost;
import net.kb150.dragoncolonies.buildings.modules.DragonStorageModule;
import net.kb150.dragoncolonies.jobs.JobBeastmaster;
import net.magister.bookofdragons.entity.data.DragonType;
import net.magister.bookofdragons.entity.stats.SpeciesStatRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
        BREEDING_DRAGONS
    }

    private BeastmasterTask currentTask = BeastmasterTask.NONE;
    private int cooldown = 0;

    // Speicher für die gerade zu verpaarenden Drachen
    private UUID breedingParent1 = null;
    private UUID breedingParent2 = null;

    public EntityAIWorkBeastmaster(@NotNull JobBeastmaster job) {
        super(job);
        
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

        if (cooldown > 0) {
            cooldown--;
            this.walkToWorkPos(this.building.getPosition()); 
            return null; 
        }

        DragonStorageModule storageModule = this.building.getStorageModule();
        if (storageModule == null) return AIWorkerState.IDLE;

        if (currentTask == BeastmasterTask.NONE) {
            // Priorität 1: Heilen (Kornblumen)
            if (scanForInjuredDragons(storageModule)) {
                return AIWorkerState.GATHERING_REQUIRED_MATERIALS; 
            } 
            // Priorität 2: Füttern (Nahrung)
            else if (scanForHungryDragons(storageModule)) {
                return AIWorkerState.GATHERING_REQUIRED_MATERIALS;
            }
            // Priorität 3: Zuchtprogramm (Zuchtfutter)
            else if (scanForBreedingPairs(storageModule)) {
                return AIWorkerState.GATHERING_REQUIRED_MATERIALS;
            }
        } else if (currentTask == BeastmasterTask.HEALING_DRAGON) {
            return processHealingTask(storageModule);
        } else if (currentTask == BeastmasterTask.FEEDING_DRAGON) {
            return processFeedingTask(storageModule);
        } else if (currentTask == BeastmasterTask.BREEDING_DRAGONS) {
            return processBreedingTask(storageModule);
        }

        cooldown = 100;
        return null;
    }

    // --- HEILUNGS-LOGIK (Kornblumen) ---

    private boolean scanForInjuredDragons(DragonStorageModule storageModule) {
        for (CompoundTag dragonTag : storageModule.getAllDragons()) {
            if (dragonTag.getBoolean("Deployed") || dragonTag.getBoolean("IsDead") || dragonTag.getBoolean("DragonColonies_IsEgg")) continue;

            float currentHealth = dragonTag.contains("Health") ? dragonTag.getFloat("Health") : 20.0f;
            float maxHealth = extractMaxHealth(dragonTag);

            // Heilen, wenn mindestens 10 HP fehlen
            if (currentHealth < maxHealth - 10.0f) {
                ItemStack healingHerb = new ItemStack(Items.CORNFLOWER, 2);

                boolean hasItemOrTransferred = this.checkIfRequestForItemExistOrCreateAsync(healingHerb, 2, 1);

                if (hasItemOrTransferred) {
                    this.currentTask = BeastmasterTask.HEALING_DRAGON;
                    return false;
                } else {
                    this.needsCurrently = new Tuple<>(stack -> stack.is(Items.CORNFLOWER), 1);
                    this.currentTask = BeastmasterTask.HEALING_DRAGON;
                    return true;
                }
            }
        }
        return false;
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

                float currentHealth = dragonTag.contains("Health") ? dragonTag.getFloat("Health") : 20.0f;
                float maxHealth = extractMaxHealth(dragonTag);

                if (currentHealth < maxHealth - 10.0f) {
                    targetDragonTag = dragonTag;
                    break;
                }
            }

            if (targetDragonTag != null) {
                // Heilkraut verbrauchen
                this.worker.getInventoryCitizen().extractItem(herbSlot, 1, false);

                // Drachen heilen (Heilt 50 HP pro Kornblume)
                float hp = targetDragonTag.contains("Health") ? targetDragonTag.getFloat("Health") : 20.0f;
                float maxHealth = extractMaxHealth(targetDragonTag);
                targetDragonTag.putFloat("Health", Math.min(maxHealth, hp + 50.0f));

                storageModule.markDirty();
                
                // Magisches Heil-Geräusch (Amethyst / XP)
                this.worker.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);

                this.currentTask = BeastmasterTask.NONE;
                cooldown = 40;
                return AIWorkerState.IDLE;
            }
        }
        
        return scanForInjuredDragons(storageModule) ? AIWorkerState.GATHERING_REQUIRED_MATERIALS : AIWorkerState.IDLE;
    }

    // --- FÜTTERUNGS-LOGIK (Nahrung) ---

    private boolean scanForHungryDragons(DragonStorageModule storageModule) {
        for (CompoundTag dragonTag : storageModule.getAllDragons()) {
            if (dragonTag.getBoolean("Deployed") || dragonTag.getBoolean("IsDead") || dragonTag.getBoolean("DragonColonies_IsEgg")) continue;

            CompoundTag needsTag = dragonTag.contains("dragonNeeds") ? dragonTag.getCompound("dragonNeeds") : new CompoundTag();
            int hunger = needsTag.contains("foodLevel") ? needsTag.getInt("foodLevel") : 100;

            if (hunger < 80) {
                DragonType type = parseDragonType(dragonTag);
                List<Item> validFoods = getValidFoodsForDragon(type);
                if (validFoods.isEmpty()) continue;

                ItemStack foodToRequest = new ItemStack(validFoods.get(0), 5);
                boolean hasItemOrTransferred = this.checkIfRequestForItemExistOrCreateAsync(foodToRequest, 5, 1);

                if (hasItemOrTransferred) {
                    this.currentTask = BeastmasterTask.FEEDING_DRAGON;
                    return false;
                } else {
                    this.needsCurrently = new Tuple<>(stack -> validFoods.contains(stack.getItem()), 1);
                    this.currentTask = BeastmasterTask.FEEDING_DRAGON;
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

            CompoundTag needsTag = dragonTag.contains("dragonNeeds") ? dragonTag.getCompound("dragonNeeds") : new CompoundTag();
            int hunger = needsTag.contains("foodLevel") ? needsTag.getInt("foodLevel") : 100;

            if (hunger < 80) {
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

            // HEILUNG DURCH FUTTER (Wie zuvor, heilt nebenbei auch HP)
            if (targetDragonTag.contains("Health")) {
                float hp = targetDragonTag.getFloat("Health");
                float maxHp = extractMaxHealth(targetDragonTag);
                if (hp < maxHp) {
                    targetDragonTag.putFloat("Health", Math.min(maxHp, hp + 10.0f));
                }
            }

            CompoundTag needsTag = targetDragonTag.contains("dragonNeeds") ? targetDragonTag.getCompound("dragonNeeds") : new CompoundTag();
            int hunger = needsTag.contains("foodLevel") ? needsTag.getInt("foodLevel") : 100;
            needsTag.putInt("foodLevel", Math.min(100, hunger + 25));
            targetDragonTag.put("dragonNeeds", needsTag);

            storageModule.markDirty();
            this.worker.playSound(net.minecraft.sounds.SoundEvents.GENERIC_EAT, 1.0f, 1.0f);

            this.currentTask = BeastmasterTask.NONE;
            cooldown = 40;
            return AIWorkerState.IDLE;
        } else {
            return scanForHungryDragons(storageModule) ? AIWorkerState.GATHERING_REQUIRED_MATERIALS : AIWorkerState.IDLE;
        }
    }


    // --- ZUCHT-LOGIK ---

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

            this.currentTask = BeastmasterTask.NONE;
            cooldown = 200;
            return AIWorkerState.IDLE;
        } else {
            return scanForBreedingPairs(storageModule) ? AIWorkerState.GATHERING_REQUIRED_MATERIALS : AIWorkerState.IDLE;
        }
    }

    // --- HILFSMETHODEN ---

    private float extractMaxHealth(CompoundTag tag) {
        if (tag.contains("Attributes", Tag.TAG_LIST)) {
            ListTag attributes = tag.getList("Attributes", Tag.TAG_COMPOUND);
            for (int i = 0; i < attributes.size(); i++) {
                CompoundTag attr = attributes.getCompound(i);
                if (attr.getString("Name").equals("minecraft:generic.max_health")) {
                    return (float) attr.getDouble("Base");
                }
            }
        }
        return tag.contains("Health") ? tag.getFloat("Health") : 20.0f;
    }

    private boolean isReadyToBreed(CompoundTag tag) {
        if (!tag.getBoolean("DragonColonies_AllowBreeding")) return false;
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

    private List<Item> getValidFoodsForDragon(DragonType type) {
        List<Item> validFoods = new ArrayList<>();
        if (type == null) {
            validFoods.add(Items.COD);
            return validFoods;
        }

        try {
            var profile = SpeciesStatRegistry.getProfile(type.getSerializedName());
            if (profile != null) {
                for (var loc : profile.favoriteFoods()) {
                    Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(loc);
                    if (item != null && !validFoods.contains(item)) validFoods.add(item);
                }
                for (var loc : profile.generalFoods()) {
                    Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(loc);
                    if (item != null && !validFoods.contains(item)) validFoods.add(item);
                }
            }
        } catch (Exception ignored) {}

        if (validFoods.isEmpty()) {
            validFoods.add(Items.COD);
            validFoods.add(Items.SALMON);
        }

        return validFoods;
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