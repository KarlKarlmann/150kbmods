package net.kb150.dragoncolonies.client.model;

import com.minecolonies.api.client.render.modeltype.CitizenModel;
import com.minecolonies.api.client.render.modeltype.IModelType;
import com.minecolonies.api.client.render.modeltype.registry.IModelTypeRegistry;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.client.model.FemaleKnightModel;
import com.minecolonies.core.client.model.MaleKnightModel;
import net.kb150.dragoncolonies.DragonColonies;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = DragonColonies.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class DragonRiderModelType implements IModelType {

    public static final ResourceLocation ID = new ResourceLocation(DragonColonies.MOD_ID, "dragonrider");

    private final MaleKnightModel maleModel = new MaleKnightModel(MaleKnightModel.createMesh().bakeRoot());
    private final FemaleKnightModel femaleModel = new FemaleKnightModel(FemaleKnightModel.createMesh().bakeRoot());

    @Override
    public ResourceLocation getName() {
        return ID;
    }

    @Override
    public CitizenModel<AbstractEntityCitizen> getMaleModel() {
        return this.maleModel;
    }

    @Override
    public CitizenModel<AbstractEntityCitizen> getFemaleModel() {
        return this.femaleModel;
    }

    @Override
    public ResourceLocation getTexture(AbstractEntityCitizen citizen) {
        String style = citizen.getEntityData().get(AbstractEntityCitizen.DATA_STYLE);
        String suffix = citizen.getEntityData().get(AbstractEntityCitizen.DATA_TEXTURE_SUFFIX);
        String gender = citizen.isFemale() ? "female" : "male";
        int textureId = (citizen.getTextureId() % 1) + 1;

        return new ResourceLocation(DragonColonies.MOD_ID, 
            "textures/entity/citizen/" + style + "/dragonrider" + gender + textureId + suffix + ".png");
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> IModelTypeRegistry.getInstance().register(new DragonRiderModelType()));
    }
}