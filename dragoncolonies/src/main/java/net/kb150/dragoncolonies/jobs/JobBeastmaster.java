package net.kb150.dragoncolonies.jobs;
import com.minecolonies.api.client.render.modeltype.ModModelTypes;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJob;
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
        return ModModelTypes.CITIZEN_ID;
    }

    @Override
    public EntityAIWorkBeastmaster generateAI() {
        return new EntityAIWorkBeastmaster(this);
    }
}