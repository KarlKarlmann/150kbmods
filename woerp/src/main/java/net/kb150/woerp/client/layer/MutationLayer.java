package net.kb150.woerp.client.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.kb150.woerp.Woerp;
import net.kb150.woerp.client.model.*;
import net.kb150.woerp.client.offset.MutationOffsetLoader;
import net.kb150.woerp.client.screen.MutationStudioScreen;
import net.kb150.woerp.client.util.ModelBoneScanner;
import net.kb150.woerp.init.ModEffects;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

public class MutationLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private static ResourceLocation tex(String name) {
        return new ResourceLocation(Woerp.MODID, "textures/entity/mutations/" + name + ".png");
    }

    private static final ResourceLocation TEX_HORN = tex("horn");
    private static final ResourceLocation TEX_EYESTALK = tex("eyestalk");
    private static final ResourceLocation TEX_EYESTALK_GLOW = tex("eyestalk_glow");
    private static final ResourceLocation TEX_VISAGE = tex("visage");
    private static final ResourceLocation TEX_VISAGE_GLOW = tex("visage_glow");
    private static final ResourceLocation TEX_SECOND_FACE = tex("second_face");
    private static final ResourceLocation TEX_SECOND_FACE_GLOW = tex("second_face_glow");
    private static final ResourceLocation TEX_PUSTULES = tex("pustules");
    private static final ResourceLocation TEX_CREST = tex("crest");
    private static final ResourceLocation TEX_SPUR = tex("spur");
    private static final ResourceLocation TEX_CARAPACE = tex("carapace");
    private static final ResourceLocation TEX_CLUBFOOT = tex("clubfoot");
    private static final ResourceLocation TEX_MAW = tex("maw");

    private final HornModel horn;
    private final EyestalkModel eyestalk;
    private final SecondFaceModel secondFace;
    private final PustulesModel pustules;
    private final CrestModel crest;
    private final SpurModel spur;
    private final CarapaceModel carapace;
    private final ClubfootModel clubfoot;
    private final MawModel maw;

    public MutationLayer(RenderLayerParent<T, M> parent, EntityModelSet modelSet) {
        super(parent);
        this.horn = new HornModel(modelSet.bakeLayer(WoerpModelLayers.HORN));
        this.eyestalk = new EyestalkModel(modelSet.bakeLayer(WoerpModelLayers.EYESTALK));
        this.secondFace = new SecondFaceModel(modelSet.bakeLayer(WoerpModelLayers.SECOND_FACE));
        this.pustules = new PustulesModel(modelSet.bakeLayer(WoerpModelLayers.PUSTULES));
        this.crest = new CrestModel(modelSet.bakeLayer(WoerpModelLayers.CREST));
        this.spur = new SpurModel(modelSet.bakeLayer(WoerpModelLayers.SPUR));
        this.carapace = new CarapaceModel(modelSet.bakeLayer(WoerpModelLayers.CARAPACE));
        this.clubfoot = new ClubfootModel(modelSet.bakeLayer(WoerpModelLayers.CLUBFOOT));
        this.maw = new MawModel(modelSet.bakeLayer(WoerpModelLayers.MAW));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) return;

        M parent = this.getParentModel();
        ModelPart head = (parent instanceof HeadedModel headed) ? headed.getHead() : null;
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0.0F);

        if (entity.hasEffect(ModEffects.MUTATION_HORN.get()))
            renderMutation("mutation_horn", horn, TEX_HORN, null, poseStack, buffer, packedLight, overlay, entity, head);

        if (entity.hasEffect(ModEffects.MUTATION_EYESTALK.get()))
            renderMutation("mutation_eyestalk", eyestalk, TEX_EYESTALK, TEX_EYESTALK_GLOW, poseStack, buffer, packedLight, overlay, entity, head);

        if (entity.hasEffect(ModEffects.MUTATION_VISAGE.get()))
            renderMutation("mutation_visage", null, TEX_VISAGE, TEX_VISAGE_GLOW, poseStack, buffer, packedLight, overlay, entity, head);

        if (entity.hasEffect(ModEffects.MUTATION_SECOND_FACE.get()))
            renderMutation("mutation_second_face", secondFace, TEX_SECOND_FACE, TEX_SECOND_FACE_GLOW, poseStack, buffer, packedLight, overlay, entity, null);

        if (entity.hasEffect(ModEffects.MUTATION_PUSTULES.get()))
            renderMutation("mutation_pustules", pustules, TEX_PUSTULES, null, poseStack, buffer, packedLight, overlay, entity, null);

        if (entity.hasEffect(ModEffects.MUTATION_CREST.get()))
            renderMutation("mutation_crest", crest, TEX_CREST, null, poseStack, buffer, packedLight, overlay, entity, null);

        if (entity.hasEffect(ModEffects.MUTATION_SPUR.get()))
            renderMutation("mutation_spur", spur, TEX_SPUR, null, poseStack, buffer, packedLight, overlay, entity, null);

        if (entity.hasEffect(ModEffects.MUTATION_CARAPACE.get()))
            renderMutation("mutation_carapace", carapace, TEX_CARAPACE, null, poseStack, buffer, packedLight, overlay, entity, null);

        if (entity.hasEffect(ModEffects.MUTATION_CLUBFOOT.get()))
            renderMutation("mutation_clubfoot", clubfoot, TEX_CLUBFOOT, null, poseStack, buffer, packedLight, overlay, entity, null);

        if (entity.hasEffect(ModEffects.MUTATION_MAW.get()))
            renderMutation("mutation_maw", maw, TEX_MAW, null, poseStack, buffer, packedLight, overlay, entity, head);
    }

    private void renderMutation(String mutationId, Model model, ResourceLocation tex, ResourceLocation glowTex, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay, T entity, ModelPart fallbackBone) {
        ResourceLocation entityRl = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());

        MutationOffsetLoader.MutationOffset offset;
        // Studio-Zustand uebersteuert den Offset live im RAM, sobald der Editor fuer diese Mutation aktiv ist
        if (MutationStudioScreen.isStudioActive && mutationId.equals(MutationStudioScreen.studioActiveMutation)) {
            offset = new MutationOffsetLoader.MutationOffset(
                MutationStudioScreen.studioSelectedBone,
                MutationStudioScreen.studioX, MutationStudioScreen.studioY, MutationStudioScreen.studioZ,
                MutationStudioScreen.studioScaleX, MutationStudioScreen.studioScaleY, MutationStudioScreen.studioScaleZ,
                MutationStudioScreen.studioRotX, MutationStudioScreen.studioRotY, MutationStudioScreen.studioRotZ
            );
        } else {
            offset = MutationOffsetLoader.getOffset(entityRl, mutationId);
        }

        poseStack.pushPose();

        boolean boneApplied = false;
        if (!offset.bone.isEmpty()) {
            boneApplied = ModelBoneScanner.applyBoneTransforms(this.getParentModel(), offset.bone, poseStack);
        }
        if (!boneApplied && fallbackBone != null) {
            fallbackBone.translateAndRotate(poseStack);
            boneApplied = true;
        }

        if (boneApplied) {
            poseStack.translate(offset.x, offset.y, offset.z);
            if (offset.rotX != 0) poseStack.mulPose(Axis.XP.rotationDegrees(offset.rotX));
            if (offset.rotY != 0) poseStack.mulPose(Axis.YP.rotationDegrees(offset.rotY));
            if (offset.rotZ != 0) poseStack.mulPose(Axis.ZP.rotationDegrees(offset.rotZ));

            // Jitter wird im Studio deaktiviert, damit die Ausrichtung auf dem Bildschirm exakt bleibt
            if (!MutationStudioScreen.isStudioActive && "mutation_horn".equals(mutationId)) {
                float jRot = ((entity.getUUID().getLeastSignificantBits() & 0xF) - 7.5F) * 2.0F;
                poseStack.mulPose(Axis.ZP.rotationDegrees(jRot));
            }

            poseStack.scale(offset.scaleX, offset.scaleY, offset.scaleZ);

            if (model != null) {
                model.renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(tex)), packedLight, overlay, 1F, 1F, 1F, 1F);
                if (glowTex != null) {
                    model.renderToBuffer(poseStack, buffer.getBuffer(RenderType.eyes(glowTex)), 15728640, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
                }
            } else if (fallbackBone != null) {
                // Decal-Mutationen (Visage) besitzen kein Mesh und rendern direkt auf die Elternflaeche
                fallbackBone.render(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(tex)), packedLight, overlay, 1F, 1F, 1F, 1F);
                if (glowTex != null) {
                    fallbackBone.render(poseStack, buffer.getBuffer(RenderType.eyes(glowTex)), 15728640, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
                }
            }
        }

        poseStack.popPose();
    }
}