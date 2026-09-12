package net.kb150.dragoncolonies.client.model;

import com.minecolonies.api.client.render.modeltype.CitizenModel;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import org.jetbrains.annotations.NotNull;

public class MaleBeastmasterModel extends CitizenModel<AbstractEntityCitizen> {

    private final ModelPart hookRightArm;
    private final ModelPart hookLeftArm;
    private final ModelPart pegRightLeg;
    private final ModelPart pegLeftLeg;
    
    private final ModelPart patchLeft;
    private final ModelPart patchRight;

    public MaleBeastmasterModel(ModelPart root) {
        super(root);
        this.hat.visible = false; 
        
        this.hookRightArm = root.getChild("right_arm").getChild("hook");
        this.hookLeftArm = root.getChild("left_arm").getChild("hook");
        this.pegRightLeg = root.getChild("right_leg").getChild("peg");
        this.pegLeftLeg = root.getChild("left_leg").getChild("peg");
        
        this.patchLeft = root.getChild("head").getChild("patch_l");
        this.patchRight = root.getChild("head").getChild("patch_r");
    }

    public static LayerDefinition createMesh() {
        MeshDefinition meshdefinition = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition head = partdefinition.addOrReplaceChild("head", CubeListBuilder.create()
            .texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
            .texOffs(32, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.5F)), 
            PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition Weldingmask = head.addOrReplaceChild("Weldingmask", CubeListBuilder.create(), PartPose.offset(0.0F, -2.6F, 0.0F));

        Weldingmask.addOrReplaceChild("band_r_r1", CubeListBuilder.create()
            .texOffs(104, 0).addBox(-4.01F, -4.0F, -4.0F, 0.0F, 1.0F, 8.0F, new CubeDeformation(0.05F))
            .texOffs(104, 0).addBox(4.26F, -4.0F, -4.0F, 0.0F, 1.0F, 8.0F, new CubeDeformation(0.05F)), 
            PartPose.offsetAndRotation(0.0F, 0.0F, -1.0F, -0.3927F, 0.0F, 0.0F));

        Weldingmask.addOrReplaceChild("band_back_r1", CubeListBuilder.create()
            .texOffs(104, 0).addBox(4.01F, -4.0F, -4.0F, 0.0F, 1.0F, 8.0F, new CubeDeformation(0.05F)), 
            PartPose.offsetAndRotation(0.25F, 1.75F, 0.0F, 0.0F, -1.5708F, 0.0F));

        Weldingmask.addOrReplaceChild("band_l_r1", CubeListBuilder.create()
            .texOffs(104, 0).addBox(4.01F, -4.0F, -4.0F, 0.0F, 1.0F, 8.0F, new CubeDeformation(0.05F)), 
            PartPose.offsetAndRotation(0.25F, -1.25F, 0.0F, 0.0F, 1.5708F, 0.0F));

        PartDefinition mask = Weldingmask.addOrReplaceChild("mask", CubeListBuilder.create(), PartPose.offset(0.25F, -4.6536F, -2.4201F));
        mask.addOrReplaceChild("mask_r1", CubeListBuilder.create()
            .texOffs(78, 41).addBox(-4.5F, -1.1619F, -2.5868F, 9.0F, 9.0F, 3.0F, new CubeDeformation(-0.2F)), 
            PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -1.7453F, 0.0F, 0.0F));

        PartDefinition patch_l = head.addOrReplaceChild("patch_l", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));
        patch_l.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(104, 8).addBox(-4.0F, -0.5F, 0.0F, 8.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.25F, -4.0F, -0.25F, 1.5708F, -1.4835F, -1.5708F));
        patch_l.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(102, 8).addBox(-7.0F, -2.0F, 1.0F, 9.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.75F, -3.0F, -5.25F, 0.0F, 0.0F, 0.5236F));
        patch_l.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(97, 0).addBox(-2.0F, -2.0F, 0.0F, 3.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.0F, -3.0F, -5.0F, 0.0F, 0.0F, 0.5236F));

        PartDefinition patch_r = head.addOrReplaceChild("patch_r", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));
        patch_r.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(104, 8).mirror().addBox(-4.0F, -0.5F, 0.0F, 8.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-4.25F, -4.0F, -0.25F, 1.5708F, 1.4835F, 1.5708F));
        patch_r.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(102, 8).mirror().addBox(-2.0F, -2.0F, 1.0F, 9.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-1.75F, -3.0F, -5.25F, 0.0F, 0.0F, -0.5236F));
        patch_r.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(97, 0).mirror().addBox(-1.0F, -2.0F, 0.0F, 3.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-2.0F, -3.0F, -5.0F, 0.0F, 0.0F, -0.5236F));

        PartDefinition body = partdefinition.addOrReplaceChild("body", CubeListBuilder.create()
            .texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.0F))
            .texOffs(16, 32).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.3F))
            .texOffs(64, 0).addBox(-4.5F, 0.0F, -2.5F, 9.0F, 14.0F, 5.0F, new CubeDeformation(0.4F)), 
            PartPose.offset(0.0F, 0.0F, 0.0F));

        body.addOrReplaceChild("toolBelt", CubeListBuilder.create()
            .texOffs(64, 20).addBox(-5.0F, 9.0F, -3.0F, 10.0F, 3.0F, 6.0F, new CubeDeformation(0.5F))
            .texOffs(64, 30).addBox(-6.0F, 10.0F, -2.0F, 3.0F, 5.0F, 4.0F, new CubeDeformation(0.0F))
            .texOffs(64, 40).addBox(3.0F, 10.0F, -2.0F, 3.0F, 5.0F, 4.0F, new CubeDeformation(0.0F)), 
            PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition cornflower = body.addOrReplaceChild("cornflower", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.2F));
        cornflower.addOrReplaceChild("cornflower_cube_r1", CubeListBuilder.create()
            .texOffs(117, 42).addBox(-2.0F, -2.5F, 0.0F, 4.0F, 5.0F, 0.0F, new CubeDeformation(0.0F)), 
            PartPose.offsetAndRotation(-0.327F, 12.9308F, 4.0F, -3.0364F, 0.1826F, 0.1914F));

        PartDefinition right_arm = partdefinition.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
        right_arm.addOrReplaceChild("normal", CubeListBuilder.create()
            .texOffs(40, 16).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F))
            .texOffs(112, 20).addBox(-3.0F, 4.0F, -2.0F, 4.0F, 8.0F, 4.0F, new CubeDeformation(0.4F)), 
            PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition right_hook = right_arm.addOrReplaceChild("hook", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));
        right_hook.addOrReplaceChild("hook_cube_r1", CubeListBuilder.create()
            .texOffs(114, 0).addBox(-1.5F, 9.0F, -2.5F, 1.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(110, 0).addBox(-1.5F, 6.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
            .texOffs(42, 17).addBox(-2.5F, -2.0F, -1.5F, 3.0F, 8.0F, 3.0F, new CubeDeformation(0.0F)), 
            PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.2618F));

        PartDefinition left_arm = partdefinition.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
        left_arm.addOrReplaceChild("normal", CubeListBuilder.create()
            .texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F))
            .texOffs(96, 20).addBox(-1.0F, 4.0F, -2.0F, 4.0F, 8.0F, 4.0F, new CubeDeformation(0.4F)), 
            PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition left_hook = left_arm.addOrReplaceChild("hook", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));
        left_hook.addOrReplaceChild("hook_cube_r2", CubeListBuilder.create()
            .texOffs(114, 0).addBox(0.5F, 9.0F, -2.5F, 1.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(110, 0).addBox(0.5F, 6.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
            .texOffs(34, 49).addBox(-0.5F, -2.0F, -1.5F, 3.0F, 8.0F, 3.0F, new CubeDeformation(0.0F)), 
            PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.2618F));

        PartDefinition right_leg = partdefinition.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
        right_leg.addOrReplaceChild("normal", CubeListBuilder.create()
            .texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F))
            .texOffs(0, 32).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.25F))
            .texOffs(80, 32).addBox(-2.0F, 7.0F, -2.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.35F)), 
            PartPose.offset(0.0F, 0.0F, 0.0F));

        right_leg.addOrReplaceChild("peg", CubeListBuilder.create()
            .texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
            .texOffs(102, 46).addBox(-0.5F, 6.0F, -0.5F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), 
            PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition left_leg = partdefinition.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
        left_leg.addOrReplaceChild("normal", CubeListBuilder.create()
            .texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F))
            .texOffs(0, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.25F))
            .texOffs(96, 32).addBox(-2.0F, 7.0F, -2.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.35F)), 
            PartPose.offset(0.0F, 0.0F, 0.0F));

        left_leg.addOrReplaceChild("peg", CubeListBuilder.create()
            .texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
            .texOffs(102, 46).addBox(-0.5F, 6.0F, -0.5F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), 
            PartPose.offset(0.0F, 0.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 128, 64);
    }

    @Override
    public void setupAnim(@NotNull AbstractEntityCitizen entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        long seed = Math.abs(entity.getUUID().getLeastSignificantBits());
        
        int rollRightArm = (int) (seed % 100);
        int rollLeftArm  = (int) ((seed / 100) % 100);
        int rollRightLeg = (int) ((seed / 10000) % 100);
        int rollLeftLeg  = (int) ((seed / 1000000) % 100);
        
        // Eigenständige RNG für die Augen
        int rollEyeLeft  = (int) ((seed / 100000000L) % 100);
        int rollEyeRight = (int) ((seed / 10000000000L) % 100);

        this.rightArm.getChild("normal").visible = (rollRightArm >= 10);
        this.hookRightArm.visible   = (rollRightArm < 10);

        this.leftArm.getChild("normal").visible  = (rollLeftArm >= 10);
        this.hookLeftArm.visible    = (rollLeftArm < 10);

        this.rightLeg.getChild("normal").visible = (rollRightLeg >= 10);
        this.pegRightLeg.visible    = (rollRightLeg < 10);

        this.leftLeg.getChild("normal").visible  = (rollLeftLeg >= 10);
        this.pegLeftLeg.visible     = (rollLeftLeg < 10);
        
        this.patchLeft.visible = (rollEyeLeft < 10);
        this.patchRight.visible = (rollEyeRight < 10);
    }
}