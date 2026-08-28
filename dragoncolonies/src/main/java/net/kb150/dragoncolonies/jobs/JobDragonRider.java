package net.kb150.dragoncolonies.jobs;

import com.minecolonies.api.client.render.modeltype.ModModelTypes;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.entity.ai.workers.guard.AbstractEntityAIGuard;
import net.kb150.dragoncolonies.ai.EntityAIWorkDragonRider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Der Drachenreiter-Beruf. Erbt von AbstractJobGuard, damit Minecolonies
 * ihn als militärische Elite-Wache mit allen Guard-Features behandelt.
 */
public class JobDragonRider extends AbstractJobGuard<JobDragonRider> {

    public static final String DESC = "com.minecolonies.coremod.job.dragonrider";

    public JobDragonRider(@NotNull ICitizenData entity) {
        super(entity);
    }

    @Override
    public ResourceLocation getModel() {
        return ModModelTypes.CITIZEN_ID;
    }

    /**
     * Erzeugt die Guard-KI für den Drachenreiter.
     * Nutzt den exakten Methodennamen generateGuardAI() der Minecolonies-API.
     */
    @Override
    public AbstractEntityAIGuard<JobDragonRider, ?> generateGuardAI() {
        return new EntityAIWorkDragonRider(this);
    }
}