package net.kb150.dragoncolonies.jobs;
import com.minecolonies.api.client.render.modeltype.ModModelTypes;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJob;
import net.kb150.dragoncolonies.ai.EntityAIWorkFirefighter;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class JobFirefighter extends AbstractJob<EntityAIWorkFirefighter, JobFirefighter> {
    
    public JobFirefighter(@NotNull ICitizenData entity) {
        super(entity);
    }

	@Override
    public ResourceLocation getModel() {
        return ModModelTypes.CITIZEN_ID;
    }

    @Override
    public EntityAIWorkFirefighter generateAI() {
        return new EntityAIWorkFirefighter(this);
    }
}