package net.kb150.dragoncolonies.jobs;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.entity.ai.workers.guard.AbstractEntityAIGuard;
import net.kb150.dragoncolonies.DragonColonies;
import net.kb150.dragoncolonies.ai.EntityAIWorkDragonRider;
import net.kb150.dragoncolonies.buildings.BuildingDragonRoost;
import net.kb150.dragoncolonies.buildings.modules.DragonStorageModule;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Der Drachenreiter-Beruf.
 * Erbt von AbstractJobGuard fuer vollen militaerischen Funktionsumfang in Minecolonies.
 * 
 * Persistiert KEINE eigene Drachen-UUID mehr:
 * Die zentrale "Single Source of Truth" liegt vollstaendig im DragonStorageModule des Horts.
 */
public class JobDragonRider extends AbstractJobGuard<JobDragonRider> {

    public static final String DESC = "com.minecolonies.coremod.job.dragonrider";

    public JobDragonRider(@NotNull ICitizenData entity) {
        super(entity);
    }

    /**
     * Ermittelt das zugewiesene Drachen-NBT direkt aus dem Hort der Wache.
     */
    public Optional<CompoundTag> getAssignedDragonData() {
        if (this.getCitizen() == null) return Optional.empty();

        // In Minecolonies gibt this.getCitizen() direkt das ICitizenData zurueck
        IBuilding workBuilding = this.getCitizen().getWorkBuilding();
        if (workBuilding instanceof BuildingDragonRoost roost) {
            DragonStorageModule storage = roost.getStorageModule();
            if (storage != null) {
                UUID guardUuid = this.getCitizen().getUUID();
                return storage.getAllDragons().stream()
                        .filter(tag -> tag.hasUUID(DragonStorageModule.TAG_GUARD_UUID) 
                                && tag.getUUID(DragonStorageModule.TAG_GUARD_UUID).equals(guardUuid))
                        .findFirst();
            }
        }
        return Optional.empty();
    }

    /**
     * Ermittelt die unveraenderliche RoostDragonID des dieser Wache zugewiesenen Drachens.
     */
    public @Nullable UUID getAssignedRoostDragonId() {
        return getAssignedDragonData()
                .filter(tag -> tag.hasUUID(DragonStorageModule.TAG_ROOST_DRAGON_ID))
                .map(tag -> tag.getUUID(DragonStorageModule.TAG_ROOST_DRAGON_ID))
                .orElse(null);
    }

    /**
     * Ermittelt die aktuell in der Spielwelt aktive Entity-UUID des zugewiesenen Drachens.
     */
    public @Nullable UUID getActiveDragonEntityUUID() {
        return getAssignedDragonData()
                .filter(tag -> tag.hasUUID(DragonStorageModule.TAG_ACTIVE_ENTITY_UUID))
                .map(tag -> tag.getUUID(DragonStorageModule.TAG_ACTIVE_ENTITY_UUID))
                .orElse(null);
    }

    /**
     * Kompatibilitaets-Getter fuer AbstractEntityAIDragonRider.
     * Fragt dynamisch die aktive Welt-UUID beim Hort ab.
     */
    public @Nullable UUID getAssignedDragonUUID() {
        return getActiveDragonEntityUUID();
    }

    /**
     * Kompatibilitaets-Setter fuer AbstractEntityAIDragonRider.
     * Falls null uebergeben wird, wird die Zuweisung im Hort geloest.
     */
    public void setAssignedDragonUUID(@Nullable UUID uuid) {
        if (uuid == null && this.getCitizen() != null) {
            unassignDragon();
        }
    }

    /**
     * Gibt den Drachen im Hort wieder frei.
     */
    public void unassignDragon() {
        if (this.getCitizen() != null) {
            IBuilding workBuilding = this.getCitizen().getWorkBuilding();
            if (workBuilding instanceof BuildingDragonRoost roost) {
                DragonStorageModule storage = roost.getStorageModule();
                if (storage != null) {
                    storage.unassignDragonFromGuard(this.getCitizen().getUUID());
                }
            }
        }
    }

    @Override
    public ResourceLocation getModel() {
        return new ResourceLocation(DragonColonies.MOD_ID, "dragonrider");
    }

    @Override
    public AbstractEntityAIGuard<JobDragonRider, ?> generateGuardAI() {
        return new EntityAIWorkDragonRider(this);
    }
}