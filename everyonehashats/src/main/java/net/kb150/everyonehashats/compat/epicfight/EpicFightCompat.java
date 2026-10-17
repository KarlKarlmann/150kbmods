package net.kb150.everyonehashats.compat.epicfight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.kb150.everyonehashats.EveryoneHasHatsEvents;
import net.kb150.everyonehashats.client.HatOffsetLoader;
import net.kb150.everyonehashats.client.HatStudioScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.client.forgeevent.PatchedRenderersEvent;
import yesman.epicfight.api.utils.math.MathUtils;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.api.utils.math.Vec3f;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;
import yesman.epicfight.client.renderer.patched.layer.PatchedLayer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

public class EpicFightCompat {

    @SubscribeEvent
    public static void onEpicFightRenderers(PatchedRenderersEvent.Modify event) {
        for (net.minecraft.world.entity.EntityType<?> type : ForgeRegistries.ENTITY_TYPES) {
            var renderer = event.get(type);
            if (renderer instanceof PatchedLivingEntityRenderer patchedLiving) {
                // Hängt unseren speziellen Epic Fight Layer in die strikte EF Whitelist
                patchedLiving.addPatchedLayerAlways(
                    net.kb150.everyonehashats.client.EveryoneHasHatsLayer.class,
                    new EpicFightHatLayer()
                );
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static class EpicFightHatLayer extends PatchedLayer {
        @Override
        protected void renderLayer(LivingEntityPatch entitypatch, LivingEntity entityliving, RenderLayer vanillaLayer, PoseStack poseStack, MultiBufferSource buffer, int packedLight, OpenMatrix4f[] poses, float bob, float yRot, float xRot, float partialTicks) {
            if (entityliving.isInvisible()) return;

            ItemStack stack = entityliving.getItemBySlot(EquipmentSlot.HEAD);
            if (!EveryoneHasHatsEvents.isHat(stack)) return;

            // 1. Epic Fight Skelett-Daten nach dem Kopf durchsuchen
            Joint headJoint = entitypatch.getArmature().searchJointByName("Head");
            if (headJoint == null) headJoint = entitypatch.getArmature().searchJointByName("head");
            if (headJoint == null) return; // Wenn die Entity keinen Kopf in EF hat, brechen wir ab

            poseStack.pushPose();

            // 2. Epic Fight rendert das Skelett kopfueber und gespiegelt.
            // Die Matrix korrigiert das Koordinatensystem und wendet exakt die vorberechnete Knochenrotation aus EF an!
            OpenMatrix4f modelMatrix = new OpenMatrix4f();
            modelMatrix.scale(new Vec3f(-1.0F, -1.0F, 1.0F))
                       .mulFront(poses[headJoint.getId()])
                       .translate(0, 0.02F, 0);

            MathUtils.mulStack(poseStack, modelMatrix);

            // Ab hier sind wir exakt im Matrix-Raum des Epic Fight Kopf-Knochens!
            // 3. Vanilla Baby-Skalierungen anwenden
            boolean isVillager = entityliving instanceof net.minecraft.world.entity.npc.Villager || entityliving instanceof net.minecraft.world.entity.monster.ZombieVillager;
            if (entityliving.isBaby() && !isVillager) {
                poseStack.translate(0.0F, 0.03125F, 0.0F);
                poseStack.scale(0.7F, 0.7F, 0.7F);
                poseStack.translate(0.0F, 1.0F, 0.0F);
            }

            // 4. Offset laden
            float x = 0.0f, y = 0.0f, z = 0.0f;
            float scaleX = 1.0f, scaleY = 1.0f, scaleZ = 1.0f;
            float rx = 0.0f, ry = 180.0f, rz = 0.0f;

            if (HatStudioScreen.isStudioActive) {
                x = HatStudioScreen.studioX; y = HatStudioScreen.studioY; z = HatStudioScreen.studioZ;
                scaleX = HatStudioScreen.studioScaleX; scaleY = HatStudioScreen.studioScaleY; scaleZ = HatStudioScreen.studioScaleZ;
                rx = HatStudioScreen.studioRotX; ry = HatStudioScreen.studioRotY; rz = HatStudioScreen.studioRotZ;
            } else {
                HatOffsetLoader.HatOffset offset = HatOffsetLoader.getOffset(ForgeRegistries.ENTITY_TYPES.getKey(entityliving.getType()));
                x = offset.x; y = offset.y; z = offset.z;
                scaleX = offset.scaleX; scaleY = offset.scaleY; scaleZ = offset.scaleZ;
                rx = offset.rotX; ry = offset.rotY; rz = offset.rotZ;
            }

            // 5. Transformationen anwenden
            poseStack.translate(x, y - 0.25F, z);
            poseStack.mulPose(Axis.XP.rotationDegrees(rx));
            poseStack.mulPose(Axis.YP.rotationDegrees(ry));
            poseStack.mulPose(Axis.ZP.rotationDegrees(rz));
            poseStack.scale(0.625F * scaleX, -0.625F * scaleY, -0.625F * scaleZ);

            // 6. Hut rendern
            Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(
                entityliving, stack, ItemDisplayContext.HEAD, false, poseStack, buffer, packedLight
            );

            poseStack.popPose();
        }
    }
}