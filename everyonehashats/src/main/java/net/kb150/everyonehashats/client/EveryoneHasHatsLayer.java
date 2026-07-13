package net.kb150.everyonehashats.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class EveryoneHasHatsLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {

    // Cache, um teure Reflection-Operationen während des Render-Ticks zu minimieren
    private static final Map<Class<?>, Field> HEAD_FIELD_CACHE = new HashMap<>();

    public EveryoneHasHatsLayer(RenderLayerParent<T, M> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity, float limbSwing, float limbSwingAmount, float partialTicks, float age, float netHeadYaw, float headPitch) {
		
        if (entity.isInvisible()) return;

        ItemStack stack = entity.getItemBySlot(EquipmentSlot.HEAD);
        if (stack.isEmpty() || !ForgeRegistries.ITEMS.getKey(stack.getItem()).getNamespace().equals("simplehats")) return;

        poseStack.pushPose();

        float x = 0.0f, y = 0.0f, z = 0.0f;
        float scaleX = 1.0f, scaleY = 1.0f, scaleZ = 1.0f;
        float rx = 0.0f, ry = 180.0f, rz = 0.0f;
        String targetBone = "";

        // =====================================================================
        // LIVE OVERRIDES FÜR DEN INGAME-EDITOR
        // =====================================================================
        if (HatStudioScreen.isStudioActive) {
            x = HatStudioScreen.studioX;
            y = HatStudioScreen.studioY;
            z = HatStudioScreen.studioZ;
            scaleX = HatStudioScreen.studioScaleX;
            scaleY = HatStudioScreen.studioScaleY;
            scaleZ = HatStudioScreen.studioScaleZ;
            rx = HatStudioScreen.studioRotX;
            ry = HatStudioScreen.studioRotY;
            rz = HatStudioScreen.studioRotZ;
            targetBone = HatStudioScreen.studioSelectedBone;
        } else {
            // Normale Offsets und Knochen aus den Resourcepacks laden
            HatOffsetLoader.HatOffset offset = HatOffsetLoader.getOffset(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()));
            x = offset.x;
            y = offset.y;
            z = offset.z;
            scaleX = offset.scaleX;
            scaleY = offset.scaleY;
            scaleZ = offset.scaleZ;
            rx = offset.rotX;
            ry = offset.rotY;
            rz = offset.rotZ;
            targetBone = offset.bone != null ? offset.bone : "";
        }

        // =====================================================================
        // KNOCHEN-TRANSFORMATION (DYNAMISCHES ATTACHMENT)
        // =====================================================================
        boolean boneApplied = false;
        if (!targetBone.isEmpty()) {
            boneApplied = ClientSetup.ModelBoneScanner.applyBoneTransforms(this.getParentModel(), targetBone, poseStack);
        }

        // Fallback: Nutze die intelligente Reflection-Heuristik aus der alten Version
        if (!boneApplied) {
            ModelPart headPart = findHeadPart(this.getParentModel());
            if (headPart != null) {
                headPart.translateAndRotate(poseStack);
            }
        }

        // Baby-Skalierung
        boolean isVillager = entity instanceof net.minecraft.world.entity.npc.Villager || entity instanceof net.minecraft.world.entity.monster.ZombieVillager;
        if (entity.isBaby() && !isVillager) {
            poseStack.translate(0.0F, 0.03125F, 0.0F);
            poseStack.scale(0.7F, 0.7F, 0.7F);
            poseStack.translate(0.0F, 1.0F, 0.0F);
        }

        // Translation anwenden (Wichtig: Vor der Skalierung!)
        poseStack.translate(x, y - 0.25F, z);

        // Rotationen keck auf die Achsen mappen (RotY standardmäßig um 180° gedreht für die Blickrichtung)
        poseStack.mulPose(Axis.XP.rotationDegrees(rx));
        poseStack.mulPose(Axis.YP.rotationDegrees(ry));
        poseStack.mulPose(Axis.ZP.rotationDegrees(rz));

        // Skalierung anwenden (Individuell für X, Y, Z inkl. Vanilla-Korrektur für Kopf-Items)
        poseStack.scale(0.625F * scaleX, -0.625F * scaleY, -0.625F * scaleZ);

        // Schreibt das Hutmodell als echtes 3D-Modell auf den Bildschirm (exakt wie in SimpleHats!)
        Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(
            entity,
            stack,
            ItemDisplayContext.HEAD,
            false,
            poseStack,
            buffer,
            packedLight
        );

        poseStack.popPose();
    }

    // Findet intelligent den Head-ModelPart – funktioniert dank Reflection bei fast jedem Modded-Tier!
    private static ModelPart findHeadPart(EntityModel<?> model) {
        if (model instanceof HeadedModel headedModel) {
            return headedModel.getHead();
        }
        
        Class<?> modelClass = model.getClass();
        Field cachedField = HEAD_FIELD_CACHE.get(modelClass);
        if (cachedField != null) {
            try {
                return (ModelPart) cachedField.get(model);
            } catch (Exception ignored) {}
        }
        
        // Durchsuche die Klassenhierarchie nach einem ModelPart-Feld, das "head" heißt oder entsprechende SRG-Mappings hat
        Class<?> current = modelClass;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getType() == net.minecraft.client.model.geom.ModelPart.class) {
                    String name = field.getName().toLowerCase(Locale.ROOT);
                    // Suchmuster für geläufige Kopf-Modellteile (inklusive Quadruped-SRG 'f_103498_' / 'f_113947_')
                    if (name.contains("head") || name.equals("f_103498_") || name.equals("f_113947_")) {
                        try {
                            field.setAccessible(true);
                            HEAD_FIELD_CACHE.put(modelClass, field);
                            return (ModelPart) field.get(model);
                        } catch (Exception ignored) {}
                    }
                }
            }
            current = current.getSuperclass();
        }
        
        // Letzter Rettungsanker: Nimm das allererste ModelPart-Feld
        current = modelClass;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getType() == net.minecraft.client.model.geom.ModelPart.class) {
                    try {
                        field.setAccessible(true);
                        HEAD_FIELD_CACHE.put(modelClass, field);
                        return (ModelPart) field.get(model);
                    } catch (Exception ignored) {}
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }
}