package net.kb150.dragoncolonies.jobs;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJob;
import net.kb150.dragoncolonies.DragonColonies;
import net.kb150.dragoncolonies.ai.EntityAIWorkBeastmaster;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class JobBeastmaster extends AbstractJob<EntityAIWorkBeastmaster, JobBeastmaster> {

    public static final String DESC = "com.minecolonies.coremod.job.beastmaster";

    public JobBeastmaster(@NotNull ICitizenData entity) {
        super(entity);
    }

    @Override
    public ResourceLocation getModel() {
        // Hier auf die neue Custom-Modell-ID verweisen
        return new ResourceLocation(DragonColonies.MOD_ID, "beastmaster");
    }

    @Override
    public EntityAIWorkBeastmaster generateAI() {
        return new EntityAIWorkBeastmaster(this);
    }
}