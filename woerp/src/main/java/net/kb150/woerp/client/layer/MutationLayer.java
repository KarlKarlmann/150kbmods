package net.kb150.woerp.client.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.kb150.woerp.Woerp;
import net.kb150.woerp.client.model.*;
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

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MutationLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    // Verhindert Performance-Einbrueche im Frame-Rendering: Speichert Feld-Referenzen pro Model-Klasse dauerhaft im RAM
    private static final Map<Class<?>, Map<String, Field>> BONE_CACHE = new HashMap<>();

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

        // Entlarvt stumme Abbrueche: Zeigt ob der Layer rendert, Effekte am Client ankommen und Knochen existieren
        if (entity.tickCount % 40 == 0) {
            M pModel = this.getParentModel();
            ModelPart hPart = getBone(pModel, "head", "f_102808_", "f_102948_", "f_103846_");
            Woerp.LOGGER.info("[WOERP-VFX-PROBE] Mob: {} | Effekte: {} | Head-Bone: {}", 
                entity.getType().getDescriptionId(), 
                entity.getActiveEffects().size(), 
                (hPart != null));
        }

        M parent = this.getParentModel();
        ModelPart head = getBone(parent, "head", "f_102808_", "f_102948_", "f_103846_");
        ModelPart body = getBone(parent, "body", "torso", "f_102810_", "f_103499_");
        ModelPart arm = getBone(parent, "rightArm", "right_arm", "leftArm", "f_102811_");
        ModelPart leg = getBone(parent, "leftLeg", "left_leg", "rightLeg", "leftHindLeg", "f_102813_");

        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0.0F);
        long seed = entity.getUUID().getLeastSignificantBits();

        if (entity.hasEffect(ModEffects.MUTATION_HORN.get())) {
            // Asymmetrischer Wuchs an linker Schlaefe; Seed-Jitter bricht starre Klon-Winkel
            float jRot = ((seed & 0xF) - 7.5F) * 2.0F;
            renderSocket(horn, head, TEX_HORN, poseStack, buffer, packedLight, overlay, -0.30F, -0.45F, -0.10F, 0, 0, jRot);
        }

        if (entity.hasEffect(ModEffects.MUTATION_EYESTALK.get())) {
            renderSocket(eyestalk, head, TEX_EYESTALK, poseStack, buffer, packedLight, overlay, 0.32F, -0.40F, -0.15F, 0, 0, 0);
            // RenderType.eyes umgeht Umgebungslicht und triggert Bloom in Shadermods
            renderGlow(eyestalk, head, TEX_EYESTALK_GLOW, poseStack, buffer, 0.32F, -0.40F, -0.15F, 0, 0, 0);
        }

        if (entity.hasEffect(ModEffects.MUTATION_VISAGE.get()) && head != null) {
            // Decal und Phosphor-Glow direkt auf Kopfgeometrie gelegt; benoetigt kein eigenes 3D-Mesh
            poseStack.pushPose();
            head.translateAndRotate(poseStack);
            head.render(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(TEX_VISAGE)), packedLight, overlay, 1F, 1F, 1F, 1F);
            head.render(poseStack, buffer.getBuffer(RenderType.eyes(TEX_VISAGE_GLOW)), 15728640, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
            poseStack.popPose();
        }

        if (entity.hasEffect(ModEffects.MUTATION_SECOND_FACE.get())) {
            renderSocket(secondFace, body, TEX_SECOND_FACE, poseStack, buffer, packedLight, overlay, -0.22F, 0.15F, -0.18F, 0, 15F, 0);
            renderGlow(secondFace, body, TEX_SECOND_FACE_GLOW, poseStack, buffer, -0.22F, 0.15F, -0.18F, 0, 15F, 0);
        }

        if (entity.hasEffect(ModEffects.MUTATION_PUSTULES.get())) {
            renderSocket(pustules, body, TEX_PUSTULES, poseStack, buffer, packedLight, overlay, 0.25F, 0.20F, 0.12F, 0, 0, 0);
        }

        if (entity.hasEffect(ModEffects.MUTATION_CREST.get())) {
            renderSocket(crest, body, TEX_CREST, poseStack, buffer, packedLight, overlay, 0.0F, 0.05F, 0.18F, 0, 0, 0);
        }

        if (entity.hasEffect(ModEffects.MUTATION_SPUR.get())) {
            // Fallback auf Torso verhindert Renderabbrueche bei armlosen Kreaturen wie Creepern
            ModelPart target = arm != null ? arm : body;
            renderSocket(spur, target, TEX_SPUR, poseStack, buffer, packedLight, overlay, arm != null ? 0.0F : -0.28F, 0.25F, 0.15F, 0, 0, 0);
        }

        if (entity.hasEffect(ModEffects.MUTATION_CARAPACE.get())) {
            renderSocket(carapace, body, TEX_CARAPACE, poseStack, buffer, packedLight, overlay, -0.28F, -0.05F, 0.0F, 0, 0, 0);
        }

        if (entity.hasEffect(ModEffects.MUTATION_CLUBFOOT.get())) {
            renderSocket(clubfoot, leg, TEX_CLUBFOOT, poseStack, buffer, packedLight, overlay, 0.0F, 0.20F, 0.0F, 0, 0, 0);
        }

        if (entity.hasEffect(ModEffects.MUTATION_MAW.get())) {
            renderSocket(maw, head, TEX_MAW, poseStack, buffer, packedLight, overlay, 0.0F, 0.0F, -0.15F, 0, 0, 0);
        }
    }

    private void renderSocket(Model model, ModelPart bone, ResourceLocation tex, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay, float tx, float ty, float tz, float rx, float ry, float rz) {
        if (bone == null) return;
        poseStack.pushPose();
        bone.translateAndRotate(poseStack);
        poseStack.translate(tx, ty, tz);
        if (rx != 0) poseStack.mulPose(Axis.XP.rotationDegrees(rx));
        if (ry != 0) poseStack.mulPose(Axis.YP.rotationDegrees(ry));
        if (rz != 0) poseStack.mulPose(Axis.ZP.rotationDegrees(rz));
        model.renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(tex)), packedLight, overlay, 1F, 1F, 1F, 1F);
        poseStack.popPose();
    }

    private void renderGlow(Model model, ModelPart bone, ResourceLocation tex, PoseStack poseStack, MultiBufferSource buffer, float tx, float ty, float tz, float rx, float ry, float rz) {
        if (bone == null) return;
        poseStack.pushPose();
        bone.translateAndRotate(poseStack);
        poseStack.translate(tx, ty, tz);
        if (rx != 0) poseStack.mulPose(Axis.XP.rotationDegrees(rx));
        if (ry != 0) poseStack.mulPose(Axis.YP.rotationDegrees(ry));
        if (rz != 0) poseStack.mulPose(Axis.ZP.rotationDegrees(rz));
        model.renderToBuffer(poseStack, buffer.getBuffer(RenderType.eyes(tex)), 15728640, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
        poseStack.popPose();
    }

	private ModelPart getBone(EntityModel<?> model, String... candidates) {
        if (candidates.length == 0) return null;
        String primary = candidates[0];

        // 1. Prioritaet: HeadedModel-Interface (Humanoids, Zombies, Skelette)
        if ("head".equals(primary) && model instanceof HeadedModel headed) {
            return headed.getHead();
        }

        // 2. Prioritaet: HierarchicalModel (Creeper, Warden, Frogs etc.) - nutzt unverschleierte String-Keys
        if (model instanceof net.minecraft.client.model.HierarchicalModel<?> hierarchical) {
            ModelPart root = hierarchical.root();
            for (String cand : candidates) {
                if (root.hasChild(cand)) {
                    return root.getChild(cand);
                }
            }
        }

        // 3. Prioritaet: Cache & Reflection fuer unstrukturierte Custom-Modelle
        Class<?> cls = model.getClass();
        Map<String, Field> map = BONE_CACHE.computeIfAbsent(cls, k -> new HashMap<>());
        if (map.containsKey(primary)) {
            Field f = map.get(primary);
            try { return f != null ? (ModelPart) f.get(model) : null; } catch (Exception e) { return null; }
        }

        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (ModelPart.class.isAssignableFrom(f.getType())) {
                    String name = f.getName().toLowerCase(Locale.ROOT);
                    for (String cand : candidates) {
                        if (name.contains(cand.toLowerCase(Locale.ROOT))) {
                            f.setAccessible(true);
                            map.put(primary, f);
                            try { return (ModelPart) f.get(model); } catch (Exception e) { return null; }
                        }
                    }
                }
            }
        }
        map.put(primary, null);
        return null;
    }
}