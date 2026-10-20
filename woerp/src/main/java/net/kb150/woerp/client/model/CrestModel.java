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

public class CrestModel extends Model {
    private final ModelPart root;

    public CrestModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // 3 Knochenplatten entlang der Z-Achse staffeln, um der Rueckenlinie zu folgen
        root.addOrReplaceChild("spike_top",
            CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -3.0F, -1.0F, 1.0F, 3.0F, 3.0F),
            PartPose.offsetAndRotation(0.0F, -4.0F, 2.0F, -0.3F, 0.0F, 0.0F));

        root.addOrReplaceChild("spike_mid",
            CubeListBuilder.create().texOffs(0, 6).addBox(-0.5F, -4.0F, -1.0F, 1.0F, 4.0F, 3.0F),
            PartPose.offsetAndRotation(0.0F, 0.0F, 2.2F, -0.15F, 0.0F, 0.0F));

        root.addOrReplaceChild("spike_bot",
            CubeListBuilder.create().texOffs(8, 0).addBox(-0.5F, -2.5F, -1.0F, 1.0F, 2.5F, 2.5F),
            PartPose.offsetAndRotation(0.0F, 4.0F, 2.0F, 0.1F, 0.0F, 0.0F));

        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}