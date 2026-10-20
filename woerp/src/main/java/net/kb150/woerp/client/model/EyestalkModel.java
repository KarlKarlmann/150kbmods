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

public class EyestalkModel extends Model {
    private final ModelPart root;

    public EyestalkModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Fleischstiel ragt nach vorn-oben; Ursprung liegt am Knochenkontakt
        PartDefinition stalk = root.addOrReplaceChild("stalk",
            CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F),
            PartPose.rotation(-0.4F, 0.2F, 0.0F));

        // Augapfel ist ueberdimensioniert, um die Silhouette der Schlaefe gezielt zu dominieren
        stalk.addOrReplaceChild("eye",
            CubeListBuilder.create().texOffs(0, 6).addBox(-1.5F, -3.0F, -2.0F, 3.0F, 3.0F, 3.0F),
            PartPose.offsetAndRotation(0.0F, -3.5F, 0.0F, 0.4F, -0.2F, 0.0F));

        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}