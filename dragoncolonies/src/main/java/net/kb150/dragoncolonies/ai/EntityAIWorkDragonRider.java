package net.kb150.dragoncolonies.ai;

import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.kb150.dragoncolonies.buildings.BuildingDragonRoost;
import net.kb150.dragoncolonies.jobs.JobDragonRider;
import org.jetbrains.annotations.NotNull;

public class EntityAIWorkDragonRider extends AbstractEntityAIDragonRider<JobDragonRider, BuildingDragonRoost> {

    public EntityAIWorkDragonRider(@NotNull JobDragonRider job) {
        super(job);
        // toolsNeeded.add() entfernt, da die Axt bereits in itemsNeeded als Waffe definiert ist
        
        new DragonRiderCombatAI((EntityCitizen) this.worker, this.getStateAI(), this);
    }

    @Override
    public Class<BuildingDragonRoost> getExpectedBuildingClass() {
        return BuildingDragonRoost.class;
    }
}