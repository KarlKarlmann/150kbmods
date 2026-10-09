// DragonStorageModule.java
package net.kb150.dragoncolonies.buildings.modules;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.colony.buildings.modules.ITickingModule;
import net.kb150.dragoncolonies.config.DragonColoniesConfig;
import net.kb150.dragoncolonies.DragonColonies;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.bookofdragons.entity.state.GroundStance;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;

public class DragonStorageModule extends AbstractBuildingModule implements IPersistentModule, ITickingModule {

    public static final String TAG_DEPLOYED = "Deployed";
    public static final String TAG_IS_DEAD = "IsDead";
    public static final String TAG_GUARD_UUID = "DragonColonies_GuardUUID";
    public static final String TAG_ROOST_DRAGON_ID = "RoostDragonID";
    public static final String TAG_ACTIVE_ENTITY_UUID = "DragonColonies_ActiveEntityUUID";

    public static final String TAG_ASSIGNMENT_MODE = "DragonColonies_AssignmentMode";
    public static final String TAG_ASSIGNED_CITIZEN_ID = "DragonColonies_AssignedCitizenId";

    public static final String MODE_AUTO = "AUTO";
    public static final String MODE_LOCKED = "LOCKED";
    public static final String MODE_BREEDING = "BREEDING";
    public static final String MODE_ASSIGNED = "ASSIGNED";

    private final List<CompoundTag> storedDragons = new ArrayList<>();

    // LRU-Cache fuer verifizierte Flugziele (Limit 150), schuetzt vor Chunk-Lags und RAM-Leaks.
    private final Map<BlockPos, BlockPos> airTargetCache = new LinkedHashMap<>(150, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<BlockPos, BlockPos> eldest) {
            return size() > 150;
        }
    };

    public DragonStorageModule() {
    }

    public BlockPos getSafeAirTarget(ServerLevel level, BlockPos rawTarget) {
        if (this.airTargetCache.containsKey(rawTarget)) {
            BlockPos cached = this.airTargetCache.get(rawTarget);
            
            // Wenn Chunk geladen ist, sicherstellen, dass nicht zwischenzeitlich gebaut wurde.
            if (level.hasChunk(cached.getX() >> 4, cached.getZ() >> 4)) {
                int surfaceY = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cached).getY();
                if (cached.getY() >= surfaceY + 12) {
                    return cached;
                } else {
                    DragonColonies.debug("NAVIGATION", "[CACHE-INVALIDATED] Ziel {} nicht mehr sicher, Oberfläche ist jetzt bei Y={}", cached.toShortString(), surfaceY);
                    this.airTargetCache.remove(rawTarget);
                    // Fallthrough für Neuberechnung
                }
            } else {
                // Chunk ungeladen, aber wir kennen das sichere Ziel von frueher -> Nutzen!
                return cached;
            }
        }

        // Chunk ist geladen und Ziel noch unbekannt -> Sicher berechnen und speichern.
        if (level.hasChunk(rawTarget.getX() >> 4, rawTarget.getZ() >> 4)) {
            int surfaceY = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, rawTarget).getY();
            BlockPos safePos = new BlockPos(rawTarget.getX(), Math.max(rawTarget.getY(), surfaceY) + 12, rawTarget.getZ());
            
            this.airTargetCache.put(rawTarget, safePos);
            this.markDirty();
            DragonColonies.debug("NAVIGATION", "[CACHE-ADD] Neues sicheres Flugziel gespeichert: {} -> {}", rawTarget.toShortString(), safePos.toShortString());
            return safePos;
        }

        // Failsafe: Chunk ungeladen UND wir haben kein gespeichertes Ziel. 
        // Sturzflug in Klippe verhindern, hoch anfliegen und NICHT speichern (wird beim Naehern ueberschrieben).
        DragonColonies.debug("NAVIGATION", "[CACHE-MISS] Unbekanntes Ziel in ungeladenem Chunk: {} -> Ausweichen nach oben.", rawTarget.toShortString());
        return new BlockPos(rawTarget.getX(), rawTarget.getY() + 40, rawTarget.getZ());
    }

    @Override
    public void onColonyTick(IColony colony) {
        boolean changed = false;
        Iterator<CompoundTag> iterator = storedDragons.iterator();
        while (iterator.hasNext()) {
            CompoundTag dragonTag = iterator.next();

            // Aktive oder tote Drachen werden nicht simuliert
            if (dragonTag.getBoolean(TAG_DEPLOYED) || dragonTag.getBoolean(TAG_IS_DEAD)) {
                continue;
            }

            int breedingCd = dragonTag.getInt("DragonColonies_BreedingCooldown");
            if (breedingCd > 0) {
                dragonTag.putInt("DragonColonies_BreedingCooldown", Math.max(0, breedingCd - 20));
                changed = true;
            }

            CompoundTag needsTag = dragonTag.contains("dragonNeeds") ? dragonTag.getCompound("dragonNeeds") : new CompoundTag();
            int foodLevel = needsTag.contains("foodLevel") ? needsTag.getInt("foodLevel") : 100;

            UUID ownerId = dragonTag.hasUUID("Owner") ? dragonTag.getUUID("Owner") : null;
            CompoundTag affectionMap = dragonTag.contains("AffectionMap") ? dragonTag.getCompound("AffectionMap") : new CompoundTag();
            int affection = (ownerId != null && affectionMap.contains(ownerId.toString())) ? affectionMap.getInt(ownerId.toString()) : 0;

            if (foodLevel > 0) {
                foodLevel -= 1;
                needsTag.putInt("foodLevel", foodLevel);
                dragonTag.put("dragonNeeds", needsTag);

                if (foodLevel > 50 && affection < 1000 && ownerId != null) {
                    affectionMap.putInt(ownerId.toString(), Math.min(1000, affection + 2));
                    dragonTag.put("AffectionMap", affectionMap);
                }
                changed = true;
            } else {
                if (affection > 0 && ownerId != null) {
                    affectionMap.putInt(ownerId.toString(), Math.max(0, affection - 25));
                    dragonTag.put("AffectionMap", affectionMap);
                    changed = true;
                } else {
                    if (!DragonColoniesConfig.isStarvationUntamingAllowed()) {
                        continue;
                    }

                    // Drache bricht aus: Nur in die Welt spawnen und Hort-Bindung auflösen
                    if (this.getBuilding() != null && this.getBuilding().getColony() != null) {
                        ServerLevel level = (ServerLevel) this.getBuilding().getColony().getWorld();
                        if (level != null && !level.isClientSide()) {
                            UUID roostDragonId = dragonTag.hasUUID(TAG_ROOST_DRAGON_ID) ? dragonTag.getUUID(TAG_ROOST_DRAGON_ID) : null;
                            DragonBase spawned = deployDragon(roostDragonId, level, this.getBuilding().getPosition(), null);
                            if (spawned != null) {
                                spawned.getPersistentData().remove("DragonColonies_RoostPos");
                                spawned.getPersistentData().remove("DragonColonies_RoostDragonID");
                                spawned.getPersistentData().remove("DragonColonies_GuardDeployed");
                                spawned.getPersistentData().remove("DragonColonies_OrphanTicks");
                                spawned.getPersistentData().remove("DragonColonies_GuardUUID");
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
        return this.getBuilding() == null ? 10 : Math.max(1, this.getBuilding().getBuildingLevel()) * 10;
    }

    public boolean canStoreMore() {
        return storedDragons.size() < getCapacity();
    }

    public boolean addDragon(CompoundTag dragonData) {
        if (!canStoreMore()) return false;

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

    public boolean isEntityValidForRoostId(UUID roostDragonId, UUID currentEntityUuid) {
        Optional<CompoundTag> opt = getDragonByRoostId(roostDragonId);
        if (opt.isEmpty()) return false;
        CompoundTag tag = opt.get();
        return tag.hasUUID(TAG_ACTIVE_ENTITY_UUID) && tag.getUUID(TAG_ACTIVE_ENTITY_UUID).equals(currentEntityUuid);
    }

    @Nullable
    public DragonBase deployDragon(UUID roostDragonId, ServerLevel level, BlockPos spawnPos, @Nullable UUID guardUuid) {
        Optional<CompoundTag> optTag;
        if (roostDragonId == null || roostDragonId.equals(new UUID(0, 0))) {
            optTag = storedDragons.stream()
                    .filter(t -> !t.getBoolean(TAG_DEPLOYED) && !t.getBoolean(TAG_IS_DEAD) && !t.getBoolean("DragonColonies_IsEgg"))
                    .findFirst();
        } else {
            optTag = getDragonByRoostId(roostDragonId);
            if (optTag.isEmpty()) optTag = getDragonByUUID(roostDragonId);
        }

        if (optTag.isEmpty()) return null;

        CompoundTag dragonTag = optTag.get();
        UUID assignedRoostId = dragonTag.hasUUID(TAG_ROOST_DRAGON_ID) ? dragonTag.getUUID(TAG_ROOST_DRAGON_ID) : UUID.randomUUID();
        dragonTag.putUUID(TAG_ROOST_DRAGON_ID, assignedRoostId);

        UUID newEntityUuid = UUID.randomUUID();
        dragonTag.putUUID("UUID", newEntityUuid);

        if (!dragonTag.contains("id") && dragonTag.contains("DragonType")) {
            dragonTag.putString("id", "bookofdragons:" + dragonTag.getString("DragonType").toLowerCase());
        }

        if (dragonTag.contains("CustomName")) {
            String rawName = dragonTag.getString("CustomName");
            if (!rawName.startsWith("{")) {
                dragonTag.putString("CustomName", Component.Serializer.toJson(Component.literal(rawName)));
            }
        }

        setDeployedStatus(assignedRoostId, true, newEntityUuid);
        if (guardUuid != null) {
            dragonTag.putUUID(TAG_GUARD_UUID, guardUuid);
        } else {
            dragonTag.remove(TAG_GUARD_UUID);
        }
        this.markDirty();

        Entity entity = EntityType.loadEntityRecursive(dragonTag, level, (e) -> {
            e.moveTo(spawnPos.getX() + 0.5, spawnPos.getY() + 1.0, spawnPos.getZ() + 0.5, 0, 0);

            BlockPos roostPos = this.getBuilding() != null ? this.getBuilding().getPosition() : spawnPos;
            e.getPersistentData().putLong("DragonColonies_RoostPos", roostPos.asLong());
            e.getPersistentData().putUUID("DragonColonies_RoostDragonID", assignedRoostId);

            if (guardUuid != null) {
                e.getPersistentData().putBoolean("DragonColonies_GuardDeployed", true);
                e.getPersistentData().putUUID("DragonColonies_GuardUUID", guardUuid);
            } else {
                e.getPersistentData().remove("DragonColonies_GuardDeployed");
                e.getPersistentData().remove("DragonColonies_GuardUUID");
            }
            return e;
        });

        if (entity instanceof DragonBase dragon) {
            dragon.setCommand(0);
            dragon.setGroundStance(GroundStance.IDLE);
            level.addFreshEntity(dragon);
            return dragon;
        }

        return null;
    }

    public boolean storeDragon(DragonBase dragon) {
        if (dragon == null || dragon.level().isClientSide()) return false;

        dragon.ejectPassengers();

        CompoundTag dragonNbt = new CompoundTag();
        dragon.saveWithoutId(dragonNbt);

        ResourceLocation entityKey = ForgeRegistries.ENTITY_TYPES.getKey(dragon.getType());
        if (entityKey != null) {
            dragonNbt.putString("id", entityKey.toString());
        }
        dragonNbt.remove("Passengers");

        String dragonDisplayName = dragon.hasCustomName() ? dragon.getCustomName().getString() : dragon.getName().getString();
        dragonNbt.putString("CustomName", Component.Serializer.toJson(Component.literal(dragonDisplayName)));
        dragonNbt.remove(TAG_GUARD_UUID);

        UUID roostDragonId = dragon.getPersistentData().hasUUID("DragonColonies_RoostDragonID")
                ? dragon.getPersistentData().getUUID("DragonColonies_RoostDragonID")
                : UUID.randomUUID();
        dragonNbt.putUUID(TAG_ROOST_DRAGON_ID, roostDragonId);

        Optional<CompoundTag> existingTag = getDragonByRoostId(roostDragonId);
        if (existingTag.isPresent()) {
            CompoundTag prev = existingTag.get();
            if (prev.contains(TAG_ASSIGNMENT_MODE)) {
                dragonNbt.putString(TAG_ASSIGNMENT_MODE, prev.getString(TAG_ASSIGNMENT_MODE));
            }
            if (prev.contains(TAG_ASSIGNED_CITIZEN_ID)) {
                dragonNbt.putInt(TAG_ASSIGNED_CITIZEN_ID, prev.getInt(TAG_ASSIGNED_CITIZEN_ID));
            }
            updateDragonData(roostDragonId, dragonNbt);
        } else {
            if (!canStoreMore()) return false;
            addDragon(dragonNbt);
            setDeployedStatus(roostDragonId, false);
        }

        dragon.getPersistentData().remove("DragonColonies_RoostPos");
        dragon.getPersistentData().remove("DragonColonies_RoostDragonID");
        dragon.getPersistentData().remove("DragonColonies_GuardDeployed");
        dragon.getPersistentData().remove("DragonColonies_OrphanTicks");
        dragon.getPersistentData().remove("DragonColonies_GuardUUID");

        dragon.discard();
        return true;
    }

    public boolean emergencyRecall(UUID dragonId, @Nullable MinecraftServer server) {
        Optional<CompoundTag> opt = getDragonByRoostId(dragonId);
        if (opt.isEmpty()) opt = getDragonByUUID(dragonId);
        if (opt.isEmpty()) return false;

        CompoundTag tag = opt.get();
        UUID roostDragonId = tag.hasUUID(TAG_ROOST_DRAGON_ID) ? tag.getUUID(TAG_ROOST_DRAGON_ID) : dragonId;
        UUID activeUuid = tag.hasUUID(TAG_ACTIVE_ENTITY_UUID) ? tag.getUUID(TAG_ACTIVE_ENTITY_UUID) : null;

        if (server != null && activeUuid != null) {
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(activeUuid);
                if (entity instanceof DragonBase dragon && dragon.isAlive()) {
                    return storeDragon(dragon);
                }
            }
        }

        return setDeployedStatus(roostDragonId, false);
    }

    public boolean setDeployedStatus(UUID dragonId, boolean isDeployed) {
        return setDeployedStatus(dragonId, isDeployed, null);
    }

    public boolean setDeployedStatus(UUID roostDragonId, boolean isDeployed, @Nullable UUID activeEntityUuid) {
        for (CompoundTag tag : storedDragons) {
            boolean matches = (tag.hasUUID(TAG_ROOST_DRAGON_ID) && tag.getUUID(TAG_ROOST_DRAGON_ID).equals(roostDragonId))
                    || (tag.hasUUID("UUID") && tag.getUUID("UUID").equals(roostDragonId));

            if (matches) {
                tag.putBoolean(TAG_DEPLOYED, isDeployed);
                if (isDeployed && activeEntityUuid != null) {
                    tag.putUUID(TAG_ACTIVE_ENTITY_UUID, activeEntityUuid);
                } else if (!isDeployed) {
                    tag.remove(TAG_ACTIVE_ENTITY_UUID);
                    tag.putUUID("UUID", UUID.randomUUID());
                }
                this.markDirty();
                return true;
            }
        }
        return false;
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

                updatedNbt.putUUID(TAG_ROOST_DRAGON_ID, preservedRoostId);
                updatedNbt.putBoolean(TAG_DEPLOYED, false);
                updatedNbt.remove(TAG_ACTIVE_ENTITY_UUID);

                if (current.contains(TAG_ASSIGNMENT_MODE) && !updatedNbt.contains(TAG_ASSIGNMENT_MODE)) {
                    updatedNbt.putString(TAG_ASSIGNMENT_MODE, current.getString(TAG_ASSIGNMENT_MODE));
                }
                if (current.contains(TAG_ASSIGNED_CITIZEN_ID) && !updatedNbt.contains(TAG_ASSIGNED_CITIZEN_ID)) {
                    updatedNbt.putInt(TAG_ASSIGNED_CITIZEN_ID, current.getInt(TAG_ASSIGNED_CITIZEN_ID));
                }

                updatedNbt.remove("DragonColonies_AllowBreeding");

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

        long[] raw = new long[this.airTargetCache.size()];
        long[] safe = new long[this.airTargetCache.size()];
        int idx = 0;
        for (Map.Entry<BlockPos, BlockPos> entry : this.airTargetCache.entrySet()) {
            raw[idx] = entry.getKey().asLong();
            safe[idx] = entry.getValue().asLong();
            idx++;
        }
        compound.putLongArray("AirTargetRaw", raw);
        compound.putLongArray("AirTargetSafe", safe);
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

        this.airTargetCache.clear();
        if (compound != null && compound.contains("AirTargetRaw") && compound.contains("AirTargetSafe")) {
            long[] raw = compound.getLongArray("AirTargetRaw");
            long[] safe = compound.getLongArray("AirTargetSafe");
            for (int i = 0; i < Math.min(raw.length, safe.length); i++) {
                this.airTargetCache.put(BlockPos.of(raw[i]), BlockPos.of(safe[i]));
            }
        }
    }
}