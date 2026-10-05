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

/**
 * Das zentrale Speichermodul des Drachenhorts.
 * Fungiert als "Single Source of Truth" fuer alle Drachen, deren Status
 * (Deployed, M.I.A., Zuweisungen an Wachen) und die unveränderliche RoostDragonID.
 */
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
     * Unbestechliche Identitätsprüfung: Eine Entität in der Welt ist genau dann
     * legitimiert, wenn ihre Entity-UUID als ActiveEntityUUID im Hort vermerkt ist.
     * Verhindert Duplikate, Geister und Relikte alter Welten ohne wackelige Flags.
     */
    public boolean isEntityValidForRoostId(UUID roostDragonId, UUID currentEntityUuid) {
        Optional<CompoundTag> opt = getDragonByRoostId(roostDragonId);
        if (opt.isEmpty()) {
            return false;
        }

        CompoundTag tag = opt.get();
        return tag.hasUUID(TAG_ACTIVE_ENTITY_UUID) && tag.getUUID(TAG_ACTIVE_ENTITY_UUID).equals(currentEntityUuid);
    }

    /**
     * Zentraler Einstiegspunkt: Drache aus dem Hort in die Welt spawnen.
     * Vergibt eine frische Entity-UUID, registriert diese als ActiveEntityUUID
     * und stempelt alle nötigen Persistent-Tags.
     */
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

        // 1. RoostDragonID garantieren
        UUID assignedRoostId = dragonTag.hasUUID(TAG_ROOST_DRAGON_ID)
                ? dragonTag.getUUID(TAG_ROOST_DRAGON_ID)
                : UUID.randomUUID();
        dragonTag.putUUID(TAG_ROOST_DRAGON_ID, assignedRoostId);

        // 2. Frische Entity-UUID erzeugen
        UUID newEntityUuid = UUID.randomUUID();
        dragonTag.putUUID("UUID", newEntityUuid);

        // 3. ID und Entity-Typ reparieren falls nötig
        if (!dragonTag.contains("id") && dragonTag.contains("DragonType")) {
            dragonTag.putString("id", "bookofdragons:" + dragonTag.getString("DragonType").toLowerCase());
        }

        // 4. CustomName JSON-Validierung
        if (dragonTag.contains("CustomName")) {
            String rawName = dragonTag.getString("CustomName");
            if (!rawName.startsWith("{")) {
                dragonTag.putString("CustomName", Component.Serializer.toJson(Component.literal(rawName)));
            }
        }

        // 5. Status im Hort stempeln
        setDeployedStatus(assignedRoostId, true, newEntityUuid);
        if (guardUuid != null) {
            dragonTag.putUUID(TAG_GUARD_UUID, guardUuid);
        } else {
            dragonTag.remove(TAG_GUARD_UUID);
        }
        this.markDirty();

        // 6. Entität instanziieren
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

    /**
     * Zentraler Einstiegspunkt: Drache aus der Welt zurück in den Hort einlagern.
     * Sichert Zustand, leert die ActiveEntityUUID, entwertet die gespeicherte UUID
     * und verwirft die Entität aus der Welt.
     */
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

        // Im Hort existieren keine ephemeren Wachen-Bindungen: Nur der persistente Spieler-Modus bleibt
        dragonNbt.remove(TAG_GUARD_UUID);

        // RoostDragonID ermitteln: Beibehalten oder neu vergeben
        UUID roostDragonId;
        if (dragon.getPersistentData().hasUUID("DragonColonies_RoostDragonID")) {
            roostDragonId = dragon.getPersistentData().getUUID("DragonColonies_RoostDragonID");
        } else {
            roostDragonId = UUID.randomUUID();
        }
        dragonNbt.putUUID(TAG_ROOST_DRAGON_ID, roostDragonId);

        // Verhindert das Ueberschreiben von Spieler-Einstellungen (Zucht, Gesperrt, Fester Reiter) beim Einlagern
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
            if (!canStoreMore()) {
                return false;
            }
            addDragon(dragonNbt);
            setDeployedStatus(roostDragonId, false);
        }

        // Tags auf der alten Entität säubern
        dragon.getPersistentData().remove("DragonColonies_RoostPos");
        dragon.getPersistentData().remove("DragonColonies_RoostDragonID");
        dragon.getPersistentData().remove("DragonColonies_GuardDeployed");
        dragon.getPersistentData().remove("DragonColonies_OrphanTicks");
        dragon.getPersistentData().remove("DragonColonies_GuardUUID");

        dragon.discard();
        return true;
    }

    /**
     * Zentraler Einstiegspunkt für Notfall-Rückrufe:
     * 1. Falls die Entität auf dem Server noch irgendwo geladen ist -> speichert Zustand,
     *    entwertet alte UUIDs und verwirft die Entität sauber via storeDragon().
     * 2. Falls Chunk entladen ist -> entwertet die UUID im Hort und setzt Deployed auf false.
     *    Sobald der alte Chunk geladen wird, greift das Mixin und verwirft die alte Entität.
     */
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
                    // Entwertung: Sobald ein Drache zurückgeholt wird, neue UUID im Hort stempeln
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

                // Welt-Entitäten kennen Spielereinstellungen nicht; verhindern, dass ein Respawn/Tod den Modus im Hort plattwalzt
                if (current.contains(TAG_ASSIGNMENT_MODE) && !updatedNbt.contains(TAG_ASSIGNMENT_MODE)) {
                    updatedNbt.putString(TAG_ASSIGNMENT_MODE, current.getString(TAG_ASSIGNMENT_MODE));
                }
                if (current.contains(TAG_ASSIGNED_CITIZEN_ID) && !updatedNbt.contains(TAG_ASSIGNED_CITIZEN_ID)) {
                    updatedNbt.putInt(TAG_ASSIGNED_CITIZEN_ID, current.getInt(TAG_ASSIGNED_CITIZEN_ID));
                }

                // Datenmüll alter Spielstände aktiv beim Speichern tilgen
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