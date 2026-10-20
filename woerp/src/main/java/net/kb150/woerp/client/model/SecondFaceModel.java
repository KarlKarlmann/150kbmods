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

public class SecondFaceModel extends Model {
    private final ModelPart root;

    public SecondFaceModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Halsstumpf verankert das Gesicht im Brustkorb
        PartDefinition neck = root.addOrReplaceChild("neck",
            CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -1.5F, -1.0F, 3.0F, 3.0F, 2.0F),
            PartPose.ZERO);

        // Schaedel ist um 12 Grad verdreht, um den Eindruck einer unvollstaendigen Teilung zu wecken
        neck.addOrReplaceChild("head",
            CubeListBuilder.create().texOffs(0, 5).addBox(-2.0F, -4.0F, -2.0F, 4.0F, 4.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, -1.0F, 0.0F, 0.1F, -0.2F, -0.15F));

        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}