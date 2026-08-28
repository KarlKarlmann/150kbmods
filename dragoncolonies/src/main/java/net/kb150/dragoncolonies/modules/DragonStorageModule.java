package net.kb150.dragoncolonies.buildings.modules;

import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.modules.ITickingModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class DragonStorageModule extends AbstractBuildingModule implements IPersistentModule, ITickingModule {

    private final List<CompoundTag> storedDragons = new ArrayList<>();
	
    public DragonStorageModule() {
    }

	@Override
    public void onColonyTick(IColony colony) {
		boolean changed = false;

		java.util.Iterator<CompoundTag> iterator = storedDragons.iterator();
		while (iterator.hasNext()) {
			CompoundTag dragonTag = iterator.next();

			// Überspringen, wenn der Drache unterwegs oder tot (M.I.A.) ist
			if (dragonTag.getBoolean("Deployed") || dragonTag.getBoolean("IsDead")) {
				continue;
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

				// 2. Sättigung bringt Vertrauen
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
					// 4. AUSBRUCH: Bindung bricht, Drache wird wild in der Welt gespawnt!
					String name = dragonTag.contains("CustomName") ? dragonTag.getString("CustomName") : "Ein Drache";
					System.out.println("[DragonColonies] " + name + " im Hort ist verhungert, bricht die Zähmung und bricht aus!");

					if (this.getBuilding() != null && this.getBuilding().getColony() != null) {
						net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) this.getBuilding().getColony().getWorld();
						if (level != null && !level.isClientSide) {
							dragonTag.putBoolean("Tame", false);
							dragonTag.remove("Owner");
							dragonTag.remove("AffectionMap");
							if (dragonTag.contains("ForgeData")) {
								CompoundTag forgeData = dragonTag.getCompound("ForgeData");
								forgeData.remove("DragonColonies_RoostPos");
								forgeData.remove("DragonColonies_GuardDeployed");
								forgeData.remove("DragonColonies_OrphanTicks");
							}							
							net.minecraft.world.entity.Entity entity = net.minecraft.world.entity.EntityType.loadEntityRecursive(dragonTag, level, (e) -> {
								BlockPos spawnPos = this.getBuilding().getPosition();
								e.moveTo(spawnPos.getX() + 0.5, spawnPos.getY() + 1.0, spawnPos.getZ() + 0.5, level.random.nextFloat() * 360f, 0);
								return e;
							});
							if (entity != null) {
								level.addFreshEntity(entity);
							}
						}
					}

					iterator.remove(); // Aus dem Hort-Speicher entfernen
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
        if (canStoreMore()) {
            storedDragons.add(dragonData);
            this.markDirty();
            return true;
        }
        return false;
    }

    public Optional<CompoundTag> getDragonByUUID(UUID dragonId) {
        return storedDragons.stream()
                .filter(tag -> tag.contains("UUID") && tag.getUUID("UUID").equals(dragonId))
                .findFirst();
    }

    public void removeDragon(UUID dragonId) {
        if (storedDragons.removeIf(tag -> tag.contains("UUID") && tag.getUUID("UUID").equals(dragonId))) {
            this.markDirty();
        }
    }
    
    // Markiert einen Drachen als "ausgeliehen" oder "zurück"
    public boolean setDeployedStatus(UUID dragonId, boolean isDeployed) {
        for (CompoundTag tag : storedDragons) {
            if (tag.hasUUID("UUID") && tag.getUUID("UUID").equals(dragonId)) {
                tag.putBoolean("Deployed", isDeployed);
                this.markDirty();
                return true;
            }
        }
        return false;
    }

    // Aktualisiert die Daten eines Drachens, wenn er zurückkehrt
    public void updateDragonData(UUID dragonId, CompoundTag updatedNbt) {
        for (int i = 0; i < storedDragons.size(); i++) {
            CompoundTag tag = storedDragons.get(i);
            if (tag.hasUUID("UUID") && tag.getUUID("UUID").equals(dragonId)) {
                updatedNbt.putBoolean("Deployed", false); // Er ist jetzt wieder da
                storedDragons.set(i, updatedNbt);
                this.markDirty();
                return;
            }
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

    // --- SYNCHRONISATION ZUM CLIENT (GUI) ---

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

    // --- PERSISTENZ (MINECOLONIES IPERSISTENTMODULE) ---

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