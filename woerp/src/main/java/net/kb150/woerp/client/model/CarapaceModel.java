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

public class CarapaceModel extends Model {
    private final ModelPart root;

    public CarapaceModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Sitzt auf der Schulter auf; ueberkragt den Armansatz, um die Silhouette einseitig massiv zu verbreitern
        PartDefinition mainPlate = root.addOrReplaceChild("main_plate",
            CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -1.5F, -3.0F, 6.0F, 3.0F, 6.0F),
            PartPose.rotation(0.0F, 0.0F, -0.2F));

        mainPlate.addOrReplaceChild("ridge",
            CubeListBuilder.create().texOffs(0, 9).addBox(-2.5F, -2.5F, -2.5F, 5.0F, 1.5F, 5.0F),
            PartPose.ZERO);

        return LayerDefinition.create(mesh, 32, 16);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}