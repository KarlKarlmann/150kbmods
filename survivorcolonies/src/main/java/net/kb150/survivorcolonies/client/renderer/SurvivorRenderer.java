package net.kb150.survivorcolonies.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.kb150.survivorcolonies.client.model.SurvivorModel;
import net.kb150.survivorcolonies.client.model.SurvivorModelLayers;
import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

public class SurvivorRenderer extends MobRenderer<SurvivorEntity, SurvivorModel> {

    private final SurvivorModel steveModel;
    private final SurvivorModel alexModel;

    public SurvivorRenderer(EntityRendererProvider.Context context) {
        // Fallback-Initialisierung mit Steve (4px Arme)
        super(context, new SurvivorModel(context.bakeLayer(SurvivorModelLayers.SURVIVOR_STEVE)), 0.5F);

        this.steveModel = this.model;
        this.alexModel = new SurvivorModel(context.bakeLayer(SurvivorModelLayers.SURVIVOR_ALEX));

        // 1. Items & Waffen in der Hand anzeigen
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));

        // 2. Rüstungsteile (Helme, Brustpanzer, Hosen, Stiefel) am Körper rendern
        this.addLayer(new HumanoidArmorLayer<>(
            this,
            new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
            new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
            context.getModelManager()
        ));
    }

    @Override
    public void render(SurvivorEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // Männlich -> Steve-Modell (4px Arme) | Weiblich -> Alex-Modell (3px Arme + Brust-Mesh + Zopf)
        this.model = entity.isFemale() ? this.alexModel : this.steveModel;
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(SurvivorEntity entity) {
        String gender = entity.isFemale() ? "female" : "male";
        String suffix = entity.getTextureSuffix(); // _a, _b, _d, _w

        // Verwendet die Knight-Texturen aus MineColonies ohne Skirt-Overlay
        return new ResourceLocation("minecolonies", 
            "textures/entity/citizen/default/knight" + gender + "1" + suffix + ".png");
    }
}