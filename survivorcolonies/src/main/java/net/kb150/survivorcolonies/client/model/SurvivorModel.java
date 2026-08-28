package net.kb150.survivorcolonies.client.model;

import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

public class SurvivorModel extends HumanoidModel<SurvivorEntity> {

    public SurvivorModel(ModelPart root) {
        super(root);
    }

    // Standard Steve (Männlich: 4px Arme)
    public static LayerDefinition createSteveLayer() {
        MeshDefinition meshdefinition = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        return LayerDefinition.create(meshdefinition, 128, 64);
    }

    // Alex Format (Weiblich: 3px Arme + Brust-Mesh + Pferdeschwanz)
    public static LayerDefinition createAlexLayer() {
        MeshDefinition meshdefinition = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition partdefinition = meshdefinition.getRoot();

        // 1. Kopf-Erweiterungen (HairExtension & Pferdeschwanz)
        PartDefinition bipedHead = partdefinition.getChild("head");
        bipedHead.addOrReplaceChild("HairExtension", CubeListBuilder.create()
                .texOffs(56, 0).addBox(-4.0F, 0.0F, 3.0F, 8.0F, 7.0F, 1.0F, new CubeDeformation(0.5F)),
                PartPose.offset(0.0F, 1.0F, 0.0F));

        PartDefinition ponytail = bipedHead.addOrReplaceChild("Ponytail", CubeListBuilder.create(), 
                PartPose.offset(0.0F, 24.0F, 0.0F));
        ponytail.addOrReplaceChild("ponyTailTip_r1", CubeListBuilder.create()
                .texOffs(88, 55).addBox(0.0F, 0.0F, 0.0F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.1F)),
                PartPose.offsetAndRotation(-0.5F, -25.0F, 4.8F, 0.2231F, 0.0F, 0.0F));
        ponytail.addOrReplaceChild("ponytailBase_r1", CubeListBuilder.create()
                .texOffs(86, 48).addBox(0.0F, 0.0F, 0.0F, 2.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-1.0F, -28.0F, 2.0F, 0.5577F, 0.0F, 0.0F));

        // 2. Körper mit Brust-Mesh
        PartDefinition bipedBody = partdefinition.getChild("body");
        bipedBody.addOrReplaceChild("breast", CubeListBuilder.create()
                .texOffs(64, 49).addBox(-3.0F, 1.8938F, -5.716F, 8.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(64, 55).addBox(-3.0F, 1.8938F, -5.716F, 8.0F, 3.0F, 3.0F, new CubeDeformation(0.25F)),
                PartPose.offsetAndRotation(-1.0F, 3.0F, 4.0F, -0.5236F, 0.0F, 0.0F));

        // 3. Alex-Arme (3px Breite)
        partdefinition.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(40, 16).addBox(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F)
                .texOffs(40, 32).addBox(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, new CubeDeformation(0.25F)),
                PartPose.offset(-5.0F, 2.5F, 0.0F));

        partdefinition.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F)
                .texOffs(48, 48).addBox(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, new CubeDeformation(0.25F)),
                PartPose.offset(5.0F, 2.5F, 0.0F));

        // Textur-Canvasgröße auf 128x64 erweitern, damit die UV-Offsets für Brust & Zopf passen
        return LayerDefinition.create(meshdefinition, 128, 64);
    }
}