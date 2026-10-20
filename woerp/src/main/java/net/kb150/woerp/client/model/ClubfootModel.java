package net.kb150.woerp.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;

public class ClubfootModel extends Model {
    private final ModelPart root;

    public ClubfootModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Umklammert das Standard-Vanilla-Bein (4x4) von aussen mit einer 5x5 Wucherung
        PartDefinition growth = root.addOrReplaceChild("growth",
            CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -3.0F, -2.5F, 5.0F, 6.0F, 5.0F),
            PartPose.offset(0.0F, 8.0F, 0.0F));

        growth.addOrReplaceChild("nodule",
            CubeListBuilder.create().texOffs(0, 11).addBox(-1.0F, -1.0F, -1.0F, 3.0F, 2.0F, 3.0F),
            PartPose.offsetAndRotation(1.8F, 1.0F, -0.5F, 0.1F, 0.3F, 0.2F));

        return LayerDefinition.create(mesh, 32, 16);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}