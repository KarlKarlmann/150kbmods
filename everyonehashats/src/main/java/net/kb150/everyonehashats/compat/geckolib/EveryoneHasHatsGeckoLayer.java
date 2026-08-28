package net.kb150.everyonehashats.compat.geckolib;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.kb150.everyonehashats.client.HatOffsetLoader;
import net.kb150.everyonehashats.client.HatStudioScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.Locale;

public class EveryoneHasHatsGeckoLayer<T extends Entity & GeoAnimatable> extends GeoRenderLayer<T> {

    public EveryoneHasHatsGeckoLayer(GeoEntityRenderer<T> entityRendererIn) {
        super(entityRendererIn);
    }

    @Override
    public void renderForBone(PoseStack poseStack, T animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        if (!(animatable instanceof LivingEntity living)) return;
        if (living.isInvisible()) return;

        ItemStack stack = living.getItemBySlot(EquipmentSlot.HEAD);
        if (stack.isEmpty() || !ForgeRegistries.ITEMS.getKey(stack.getItem()).getNamespace().equals("simplehats")) return;

        float x = 0.0f, y = 0.0f, z = 0.0f;
        float scaleX = 1.0f, scaleY = 1.0f, scaleZ = 1.0f;
        float rx = 0.0f, ry = 180.0f, rz = 0.0f;
        String targetBoneName = "";

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
            targetBoneName = HatStudioScreen.studioSelectedBone;
        } else {
            HatOffsetLoader.HatOffset offset = HatOffsetLoader.getOffset(ForgeRegistries.ENTITY_TYPES.getKey(living.getType()));
            x = offset.x;
            y = offset.y;
            z = offset.z;
            scaleX = offset.scaleX;
            scaleY = offset.scaleY;
            scaleZ = offset.scaleZ;
            rx = offset.rotX;
            ry = offset.rotY;
            rz = offset.rotZ;
            targetBoneName = offset.bone != null ? offset.bone : "";
        }

        String realBoneName = targetBoneName;
        if (realBoneName.contains("/")) {
            realBoneName = realBoneName.substring(realBoneName.lastIndexOf('/') + 1);
        }

        if (realBoneName.isEmpty()) {
            String name = bone.getName().toLowerCase(Locale.ROOT);
            if (!(name.equals("head") || name.equals("armorhead") || name.equals("bipedhead"))) {
                return;
            }
        } else {
            if (!bone.getName().equals(realBoneName)) {
                return;
            }
        }

        poseStack.pushPose();
        
        poseStack.translate(x, y - 0.25F, z);
        poseStack.mulPose(Axis.XP.rotationDegrees(rx));
        poseStack.mulPose(Axis.YP.rotationDegrees(ry));
        poseStack.mulPose(Axis.ZP.rotationDegrees(rz));
        poseStack.scale(0.625F * scaleX, -0.625F * scaleY, -0.625F * scaleZ);

        // Hut normal im gemeinsamen Render-Puffer zeichnen
        Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(
            living, stack, ItemDisplayContext.HEAD, false, poseStack, bufferSource, packedLight
        );

        poseStack.popPose();

        // FIX: Switcht die MultiBufferSource zurück auf den RenderType des Mobs!
        bufferSource.getBuffer(renderType);
    }
}