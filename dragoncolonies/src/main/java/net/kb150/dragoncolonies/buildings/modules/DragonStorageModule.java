package net.kb150.dragoncolonies.buildings.modules;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.colony.buildings.modules.ITickingModule;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Das zentrale Speichermodul des Drachenhorts.
 * Fungiert als "Single Source of Truth" fuer alle Drachen, deren Status
 * (Deployed, M.I.A., Zuweisungen an Wachen) und die unveränderliche RoostDragonID.
 */
public class DragonStorageModule extends AbstractBuildingModule implements IPersistentModule, ITickingModule {

    public static final String TAG_ROOST_DRAGON_ID = "RoostDragonID";
    public static final String TAG_ACTIVE_ENTITY_UUID = "ActiveEntityUUID";
    public static final String TAG_GUARD_UUID = "DragonColonies_GuardUUID";
    public static final String TAG_DEPLOYED = "Deployed";
    public static final String TAG_IS_DEAD = "IsDead";

    private final List<CompoundTag> storedDragons = new ArrayList<>();

    public DragonStorageModule() {
    }

    @Override
    public void onColonyTick(IColony colony) {
        boolean changed = false;
        Iterator<CompoundTag> iterator = storedDragons.iterator();
        while (iterator.hasNext()) {
            CompoundTag dragonTag = iterator.next();

            // Ueberspringen, wenn der Drache unterwegs oder tot (M.I.A.) ist
            if (dragonTag.getBoolean(TAG_DEPLOYED) || dragonTag.getBoolean(TAG_IS_DEAD)) {
                continue;
            }
			int breedingCd = dragonTag.getInt("DragonColonies_BreedingCooldown");
			if (breedingCd > 0) {
				dragonTag.putInt("DragonColonies_BreedingCooldown", Math.max(0, breedingCd - 20));
				changed = true;
			}

            // Hunger auslesen
            CompoundTag needsTag = dragonTag.contains("dragonNeeds") ? dragonTag.getCompound("dragonNeeds") : new CompoundTag();
            int foodLevel = needsTag.contains("foodLevel") ? needsTag.getInt("foodLevel") : 100;

            // Besitzer-UUID auslesen
            UUID ownerId = dragonTag.hasUUID("Owner") ? dragonTag.getUUID("Owner") : null;

            // AffectionMap auslesen
            CompoundTag affectionMap = dragonTag.contains("AffectionMap") ? dragonTag.getCompound("AffectionMap") : new CompoundTag();
            int affection = 0;

            if (ownerId != null && affectionMap.contains(ownerId.toString())) {
                affection = affectionMap.getInt(ownerId.toString());
            }

            if (foodLevel > 0) {
                // 1. Verdauung
                foodLevel -= 1;
                needsTag.putInt("foodLevel", foodLevel);
                dragonTag.put("dragonNeeds", needsTag);

                // 2. Saettigung bringt Vertrauen
                if (foodLevel > 50 && affection < 1000 && ownerId != null) {
                    affectionMap.putInt(ownerId.toString(), Math.min(1000, affection + 2));
                    dragonTag.put("AffectionMap", affectionMap);
                }
                changed = true;

            } else {
                // 3. Hunger senkt Vertrauen
                if (affection > 0 && ownerId != null) {
                    affectionMap.putInt(ownerId.toString(), Math.max(0, affection - 25));
                    dragonTag.put("AffectionMap", affectionMap);
                    changed = true;
                } else {
                    // 4. AUSBRUCH: Bindung bricht, Drache wird wild in der Welt gespawnt
                    String name = dragonTag.contains("CustomName") ? dragonTag.getString("CustomName") : "Ein Drache";
                    //System.out.println("[DragonColonies] " + name + " im Hort ist verhungert, bricht die Zähmung und bricht aus!");

                    if (this.getBuilding() != null && this.getBuilding().getColony() != null) {
                        ServerLevel level = (ServerLevel) this.getBuilding().getColony().getWorld();
                        if (level != null && !level.isClientSide()) {
                            dragonTag.putBoolean("Tame", false);
                            dragonTag.remove("Owner");
                            dragonTag.remove("AffectionMap");
                            if (dragonTag.contains("ForgeData")) {
                                CompoundTag forgeData = dragonTag.getCompound("ForgeData");
                                forgeData.remove("DragonColonies_RoostPos");
                                forgeData.remove("DragonColonies_GuardDeployed");
                                forgeData.remove("DragonColonies_OrphanTicks");
                            }

                            Entity entity = EntityType.loadEntityRecursive(dragonTag, level, (e) -> {
                                BlockPos spawnPos = this.getBuilding().getPosition();
                                e.moveTo(spawnPos.getX() + 0.5, spawnPos.getY() + 1.0, spawnPos.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
                                return e;
                            });
                            if (entity != null) {
                                level.addFreshEntity(entity);
                            }
                        }
                    }

                    iterator.remove();
                    changed = true;
                }
            }
        }

        if (changed) {
            this.markDirty();
        }
    }

    public int getCapacity() {
        if (this.getBuilding() == null) return 10;
        int level = Math.max(1, this.getBuilding().getBuildingLevel());
        return level * 10;
    }

    public boolean canStoreMore() {
        return storedDragons.size() < getCapacity();
    }

    public boolean addDragon(CompoundTag dragonData) {
        if (!canStoreMore()) return false;

        // RoostDragonID sicherstellen
        if (!dragonData.hasUUID(TAG_ROOST_DRAGON_ID)) {
            dragonData.putUUID(TAG_ROOST_DRAGON_ID, UUID.randomUUID());
        }

        storedDragons.add(dragonData);
        this.markDirty();
        return true;
    }

    public Optional<CompoundTag> getDragonByRoostId(UUID roostDragonId) {
        return storedDragons.stream()
                .filter(tag -> tag.hasUUID(TAG_ROOST_DRAGON_ID) && tag.getUUID(TAG_ROOST_DRAGON_ID).equals(roostDragonId))
                .findFirst();
    }

    public Optional<CompoundTag> getDragonByUUID(UUID dragonId) {
        return storedDragons.stream()
                .filter(tag -> (tag.hasUUID(TAG_ACTIVE_ENTITY_UUID) && tag.getUUID(TAG_ACTIVE_ENTITY_UUID).equals(dragonId))
                        || (tag.hasUUID(TAG_ROOST_DRAGON_ID) && tag.getUUID(TAG_ROOST_DRAGON_ID).equals(dragonId))
                        || (tag.hasUUID("UUID") && tag.getUUID("UUID").equals(dragonId)))
                .findFirst();
    }

    public Optional<CompoundTag> getDragonForGuard(UUID guardUuid) {
        return storedDragons.stream()
                .filter(tag -> tag.hasUUID(TAG_GUARD_UUID) && tag.getUUID(TAG_GUARD_UUID).equals(guardUuid))
                .findFirst();
    }

    /**
     * Validiert, ob eine aufwachende Minecraft-Entitaet die aktuell zulaessige
     * Auspraegung ihrer RoostDragonID darstellt. Verhindert Duplikate bei Chunks.
     */
    public boolean isEntityValidForRoostId(UUID roostDragonId, UUID currentEntityUuid) {
        Optional<CompoundTag> opt = getDragonByRoostId(roostDragonId);
        if (opt.isEmpty()) {
            return false;
        }

        CompoundTag tag = opt.get();
        if (!tag.getBoolean(TAG_DEPLOYED) || tag.getBoolean(TAG_IS_DEAD)) {
            return false;
        }

        if (tag.hasUUID(TAG_ACTIVE_ENTITY_UUID)) {
            return tag.getUUID(TAG_ACTIVE_ENTITY_UUID).equals(currentEntityUuid);
        }

        return tag.hasUUID("UUID") && tag.getUUID("UUID").equals(currentEntityUuid);
    }

    public boolean setDeployedStatus(UUID roostDragonId, boolean isDeployed, UUID activeEntityUuid) {
        for (CompoundTag tag : storedDragons) {
            boolean matches = (tag.hasUUID(TAG_ROOST_DRAGON_ID) && tag.getUUID(TAG_ROOST_DRAGON_ID).equals(roostDragonId))
                    || (tag.hasUUID("UUID") && tag.getUUID("UUID").equals(roostDragonId));

            if (matches) {
                tag.putBoolean(TAG_DEPLOYED, isDeployed);
                if (isDeployed && activeEntityUuid != null) {
                    tag.putUUID(TAG_ACTIVE_ENTITY_UUID, activeEntityUuid);
                } else if (!isDeployed) {
                    tag.remove(TAG_ACTIVE_ENTITY_UUID);
                }
                this.markDirty();
                return true;
            }
        }
        return false;
    }

    public boolean setDeployedStatus(UUID dragonId, boolean isDeployed) {
        return setDeployedStatus(dragonId, isDeployed, null);
    }

    public void updateDragonData(UUID roostDragonId, CompoundTag updatedNbt) {
        for (int i = 0; i < storedDragons.size(); i++) {
            CompoundTag current = storedDragons.get(i);
            boolean matches = (current.hasUUID(TAG_ROOST_DRAGON_ID) && current.getUUID(TAG_ROOST_DRAGON_ID).equals(roostDragonId))
                    || (current.hasUUID("UUID") && current.getUUID("UUID").equals(roostDragonId));

            if (matches) {
                UUID preservedRoostId = current.hasUUID(TAG_ROOST_DRAGON_ID)
                        ? current.getUUID(TAG_ROOST_DRAGON_ID)
                        : roostDragonId;

                UUID preservedGuard = current.hasUUID(TAG_GUARD_UUID)
                        ? current.getUUID(TAG_GUARD_UUID)
                        : null;

                updatedNbt.putUUID(TAG_ROOST_DRAGON_ID, preservedRoostId);
                updatedNbt.putBoolean(TAG_DEPLOYED, false);
                updatedNbt.remove(TAG_ACTIVE_ENTITY_UUID);

                if (preservedGuard != null && !updatedNbt.hasUUID(TAG_GUARD_UUID)) {
                    updatedNbt.putUUID(TAG_GUARD_UUID, preservedGuard);
                }

                storedDragons.set(i, updatedNbt);
                this.markDirty();
                return;
            }
        }
    }

    public void unassignDragonFromGuard(UUID guardUuid) {
        boolean changed = false;
        for (CompoundTag tag : storedDragons) {
            if (tag.hasUUID(TAG_GUARD_UUID) && tag.getUUID(TAG_GUARD_UUID).equals(guardUuid)) {
                tag.remove(TAG_GUARD_UUID);
                changed = true;
            }
        }
        if (changed) {
            this.markDirty();
        }
    }

    public void removeDragon(UUID dragonId) {
        boolean removed = storedDragons.removeIf(tag ->
                (tag.hasUUID(TAG_ROOST_DRAGON_ID) && tag.getUUID(TAG_ROOST_DRAGON_ID).equals(dragonId))
                        || (tag.hasUUID(TAG_ACTIVE_ENTITY_UUID) && tag.getUUID(TAG_ACTIVE_ENTITY_UUID).equals(dragonId))
                        || (tag.hasUUID("UUID") && tag.getUUID("UUID").equals(dragonId))
        );

        if (removed) {
            this.markDirty();
        }
    }

    public List<CompoundTag> getAllDragons() {
        return new ArrayList<>(storedDragons);
    }

    public List<CompoundTag> getStoredDragons() {
        return storedDragons;
    }

    @Override
    public void markDirty() {
        super.markDirty();
        if (this.getBuilding() != null) {
            this.getBuilding().markDirty();
        }
    }

    @Override
    public void serializeToView(FriendlyByteBuf buf) {
        super.serializeToView(buf);
        CompoundTag syncTag = new CompoundTag();
        ListTag dragonList = new ListTag();
        for (CompoundTag dragonTag : storedDragons) {
            dragonList.add(dragonTag);
        }
        syncTag.put("StoredDragons", dragonList);
        syncTag.putInt("Capacity", getCapacity());
        buf.writeNbt(syncTag);
    }

    @Override
    public void serializeNBT(CompoundTag compound) {
        ListTag dragonList = new ListTag();
        for (CompoundTag dragonTag : storedDragons) {
            dragonList.add(dragonTag);
        }
        compound.put("StoredDragons", dragonList);
    }

    @Override
    public void deserializeNBT(CompoundTag compound) {
        storedDragons.clear();
        if (compound != null && compound.contains("StoredDragons", Tag.TAG_LIST)) {
            ListTag dragonList = compound.getList("StoredDragons", Tag.TAG_COMPOUND);
            for (int i = 0; i < dragonList.size(); i++) {
                storedDragons.add(dragonList.getCompound(i));
            }
        }
    }
}