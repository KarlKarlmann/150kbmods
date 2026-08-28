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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EntityAIWorkBeastmaster extends AbstractEntityAIBasic<JobBeastmaster, BuildingDragonRoost> {

    private enum BeastmasterTask {
        NONE,
        HEALING_DRAGON
    }

    private BeastmasterTask currentTask = BeastmasterTask.NONE;
    private int cooldown = 0;

    public EntityAIWorkBeastmaster(@NotNull JobBeastmaster job) {
        super(job);
        
        // Minecolonies States registrieren
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
            if (scanForInjuredDragons(storageModule)) {
                // Futter fehlt -> Wechselt in den Materialbeschaffungs-State von Minecolonies
                return AIWorkerState.GATHERING_REQUIRED_MATERIALS; 
            }
        } else if (currentTask == BeastmasterTask.HEALING_DRAGON) {
            return processHealingTask(storageModule);
        }

        cooldown = 100;
        return null;
    }

    private boolean scanForInjuredDragons(DragonStorageModule storageModule) {
        for (CompoundTag dragonTag : storageModule.getAllDragons()) {
            if (dragonTag.getBoolean("Deployed") || dragonTag.getBoolean("IsDead")) continue;

            float currentHealth = dragonTag.contains("Health") ? dragonTag.getFloat("Health") : 20.0f;
            CompoundTag needsTag = dragonTag.contains("dragonNeeds") ? dragonTag.getCompound("dragonNeeds") : new CompoundTag();
            int hunger = needsTag.contains("foodLevel") ? needsTag.getInt("foodLevel") : 100;

            if (currentHealth < 20.0f || hunger < 80) {
                DragonType type = parseDragonType(dragonTag);
                List<Item> validFoods = getValidFoodsForDragon(type);
                if (validFoods.isEmpty()) continue;

                ItemStack foodToRequest = new ItemStack(validFoods.get(0), 5);

                // NATIVE MINECOLONIES REQUEST LOGIK:
                // Prüft Bürger-Inventar & Hütten-Kisten. Fehlt das Item, erstellt es automatisch Kurier-Requests.
                boolean hasItemOrTransferred = this.checkIfRequestForItemExistOrCreateAsync(foodToRequest, 5, 1);

                if (hasItemOrTransferred) {
                    // Futter ist direkt im Inventar verfügbar
                    this.currentTask = BeastmasterTask.HEALING_DRAGON;
                    return false;
                } else {
                    // Futter muss beschafft/geliefert werden -> setzt Suchfilter für GATHERING_REQUIRED_MATERIALS
                    this.needsCurrently = new Tuple<>(stack -> validFoods.contains(stack.getItem()), 1);
                    this.currentTask = BeastmasterTask.HEALING_DRAGON;
                    return true;
                }
            }
        }
        return false;
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

    private IAIState processHealingTask(DragonStorageModule storageModule) {
        int foodSlot = -1;
        CompoundTag targetDragonTag = null;

        for (CompoundTag dragonTag : storageModule.getStoredDragons()) {
            if (dragonTag.getBoolean("Deployed") || dragonTag.getBoolean("IsDead")) continue;

            float currentHealth = dragonTag.contains("Health") ? dragonTag.getFloat("Health") : 20.0f;
            CompoundTag needsTag = dragonTag.contains("dragonNeeds") ? dragonTag.getCompound("dragonNeeds") : new CompoundTag();
            int hunger = needsTag.contains("foodLevel") ? needsTag.getInt("foodLevel") : 100;

            if (currentHealth < 20.0f || hunger < 100) {
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

            if (targetDragonTag.contains("Health")) {
                float hp = targetDragonTag.getFloat("Health");
                if (hp < 20.0f) {
                    targetDragonTag.putFloat("Health", Math.min(20.0f, hp + 10.0f));
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
            return scanForInjuredDragons(storageModule) ? AIWorkerState.GATHERING_REQUIRED_MATERIALS : AIWorkerState.IDLE;
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