package net.kb150.everyonehashats.compat.azurelib;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.kb150.everyonehashats.EveryoneHasHatsEvents;
import net.kb150.everyonehashats.client.HatOffsetLoader;
import net.kb150.everyonehashats.client.HatStudioScreen;
import net.kb150.everyonehashats.EveryoneHasHats;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import mod.azure.azurelib.model.AzBone;
import mod.azure.azurelib.render.AzRendererPipelineContext;
import mod.azure.azurelib.render.layer.AzRenderLayer;
import mod.azure.azurelib.util.client.RenderUtils;

import java.util.Locale;
import java.util.UUID;

public class EveryoneHasHatsAzureLayer<T extends Entity> implements AzRenderLayer<UUID, T> {

    @Override
    public void preRender(AzRendererPipelineContext<UUID, T> context) {}

    @Override
    public void render(AzRendererPipelineContext<UUID, T> context) {}

	@Override
    public void renderForBone(AzRendererPipelineContext<UUID, T> context, AzBone bone) {
        Entity entity = context.animatable();
        if (!(entity instanceof LivingEntity living) || living.isInvisible()) return;

        ItemStack stack = living.getItemBySlot(EquipmentSlot.HEAD);
        if (!EveryoneHasHatsEvents.isHat(stack)) return;

        float x = 0.0f, y = 0.0f, z = 0.0f;
        float scaleX = 1.0f, scaleY = 1.0f, scaleZ = 1.0f;
        float rx = 0.0f, ry = 180.0f, rz = 0.0f;
        String targetBoneName = "";

        if (HatStudioScreen.isStudioActive) {
            x = HatStudioScreen.studioX; y = HatStudioScreen.studioY; z = HatStudioScreen.studioZ;
            scaleX = HatStudioScreen.studioScaleX; scaleY = HatStudioScreen.studioScaleY; scaleZ = HatStudioScreen.studioScaleZ;
            rx = HatStudioScreen.studioRotX; ry = HatStudioScreen.studioRotY; rz = HatStudioScreen.studioRotZ;
            targetBoneName = HatStudioScreen.studioSelectedBone;
        } else {
            HatOffsetLoader.HatOffset offset = HatOffsetLoader.getOffset(ForgeRegistries.ENTITY_TYPES.getKey(living.getType()));
            x = offset.x; y = offset.y; z = offset.z;
            scaleX = offset.scaleX; scaleY = offset.scaleY; scaleZ = offset.scaleZ;
            rx = offset.rotX; ry = offset.rotY; rz = offset.rotZ;
            targetBoneName = offset.bone != null ? offset.bone : "";
        }

        String realBoneName = targetBoneName.contains("/") ? targetBoneName.substring(targetBoneName.lastIndexOf('/') + 1) : targetBoneName;

        if (realBoneName.isEmpty()) {
            String name = bone.getName().toLowerCase(Locale.ROOT);
            if (!(name.equals("head") || name.equals("armorhead") || name.equals("bipedhead"))) return;
        } else {
            if (!bone.getName().equals(realBoneName)) return;
        }

        PoseStack poseStack = context.poseStack();
        poseStack.pushPose();

        // AzureLib Spezifisch: Wir müssen die Matrix erst auf den Knochen transformieren!
        RenderUtils.translateAndRotateMatrixForBone(poseStack, bone);

        // Hut Offsets anwenden
        poseStack.translate(x, y - 0.25F, z);
        poseStack.mulPose(Axis.XP.rotationDegrees(rx));
        poseStack.mulPose(Axis.YP.rotationDegrees(ry));
        poseStack.mulPose(Axis.ZP.rotationDegrees(rz));
        poseStack.scale(0.625F * scaleX, -0.625F * scaleY, -0.625F * scaleZ);

        Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(
            living, stack, ItemDisplayContext.HEAD, false, poseStack, context.multiBufferSource(), context.packedLight()
        );

        poseStack.popPose();

        // AzureLib Spezifisch: Den Buffer für nachfolgende Render-Operationen wiederherstellen
        context.setVertexConsumer(context.multiBufferSource().getBuffer(context.renderType()));
    }
}