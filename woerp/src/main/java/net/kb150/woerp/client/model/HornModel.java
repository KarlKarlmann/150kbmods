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

public class HornModel extends Model {
    private final ModelPart root;

    public HornModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Basiskubus ragt 0.5 Pixel in den Kopf hinein, um Nahtstellen bei Kopfdrehungen zu verdecken
        PartDefinition base = root.addOrReplaceChild("base", 
            CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F),
            PartPose.ZERO);

        // Diskrete Neigung bricht die Wuchsrichtung nach schraeg oben-aussen
        PartDefinition mid = base.addOrReplaceChild("mid",
            CubeListBuilder.create().texOffs(0, 6).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F),
            PartPose.offsetAndRotation(0.0F, -1.0F, 0.0F, -0.25F, 0.0F, 0.35F));

        mid.addOrReplaceChild("tip",
            CubeListBuilder.create().texOffs(8, 0).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F),
            PartPose.offsetAndRotation(0.0F, -2.5F, 0.0F, -0.30F, 0.0F, 0.15F));

        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}