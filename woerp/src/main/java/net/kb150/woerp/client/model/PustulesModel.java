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

public class PustulesModel extends Model {
    private final ModelPart root;

    public PustulesModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // 3 ungleich grosse Beulen ueberlappen einander, um organische Schwellung darzustellen
        root.addOrReplaceChild("bubble_main",
            CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -2.0F, -1.5F, 4.0F, 4.0F, 3.0F),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        root.addOrReplaceChild("bubble_small",
            CubeListBuilder.create().texOffs(0, 7).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F),
            PartPose.offset(1.8F, -1.5F, 0.5F));

        root.addOrReplaceChild("bubble_tiny",
            CubeListBuilder.create().texOffs(8, 7).addBox(-0.75F, -0.75F, -0.75F, 1.5F, 1.5F, 1.5F),
            PartPose.offset(-1.5F, 1.2F, 0.8F));

        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}