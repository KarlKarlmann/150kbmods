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

public class SpurModel extends Model {
    private final ModelPart root;

    public SpurModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Zeigt nach hinten-unten; schneidet bei Schwung-Animationen synchron durch den Raum
        PartDefinition base = root.addOrReplaceChild("base",
            CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 3.0F),
            PartPose.rotation(0.4F, 0.0F, 0.0F));

        base.addOrReplaceChild("tip",
            CubeListBuilder.create().texOffs(0, 5).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 4.0F),
            PartPose.offsetAndRotation(0.0F, 0.0F, 2.8F, 0.2F, 0.0F, 0.0F));

        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}