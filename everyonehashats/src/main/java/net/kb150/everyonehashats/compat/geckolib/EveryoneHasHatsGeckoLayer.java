package net.kb150.everyonehashats.compat.geckolib;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.kb150.everyonehashats.client.HatOffsetLoader;
import net.kb150.everyonehashats.client.HatStudioScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.Locale;

public class EveryoneHasHatsGeckoLayer<T extends LivingEntity & GeoAnimatable> extends GeoRenderLayer<T> {

    public EveryoneHasHatsGeckoLayer(GeoEntityRenderer<T> entityRendererIn) {
        super(entityRendererIn);
    }

    /**
     * WICHTIG: Für GeckoLib dürfen wir nicht "render" benutzen, sondern müssen uns über
     * "renderForBone" exakt in den Moment einklinken, wenn GeckoLib den jeweiligen Knochen zeichnet.
     * Nur so erben wir automatisch alle komplexen Eltern-Rotationen (Atmen, Wackeln, etc.) im PoseStack!
     */
    @Override
    public void renderForBone(PoseStack poseStack, T animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        if (animatable.isInvisible()) return;

        ItemStack stack = animatable.getItemBySlot(EquipmentSlot.HEAD);
        if (stack.isEmpty() || !ForgeRegistries.ITEMS.getKey(stack.getItem()).getNamespace().equals("simplehats")) return;

        float x = 0.0f, y = 0.0f, z = 0.0f;
        float scaleX = 1.0f, scaleY = 1.0f, scaleZ = 1.0f;
        float rx = 0.0f, ry = 180.0f, rz = 0.0f;
        String targetBoneName = "";

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
            targetBoneName = HatStudioScreen.studioSelectedBone;
        } else {
            // Lade Offsets (Studio vs Ingame)
            HatOffsetLoader.HatOffset offset = HatOffsetLoader.getOffset(ForgeRegistries.ENTITY_TYPES.getKey(animatable.getType()));
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

        // =====================================================================
        // IDENTIFIKATION DES AKTUELLEN KNOCHENS
        // =====================================================================
        String realBoneName = targetBoneName;
        // Da das Studio Pfade wie "root/body/head" speichert, schneiden wir für GeckoLib den End-Knochen ab
        if (realBoneName.contains("/")) {
            realBoneName = realBoneName.substring(realBoneName.lastIndexOf('/') + 1);
        }

        if (realBoneName.isEmpty()) {
            // Smarte Fallback-Heuristik für Geckolib: Versuche typische Kopf-Namen, falls kein Custom-Knochen in der JSON steht
            String name = bone.getName().toLowerCase(Locale.ROOT);
            if (!(name.equals("head") || name.equals("armorhead") || name.equals("bipedhead"))) {
                return; // Wenn dies nicht der Kopf ist, zeichne hier keinen Hut
            }
        } else {
            if (!bone.getName().equals(realBoneName)) {
                return; // Wir warten, bis GeckoLib bei dem Knochen ankommt, den wir in der JSON gespeichert haben
            }
        }

        // =====================================================================
        // UNSERE CUSTOM TRANSFORMATIONEN (PoseStack ist durch GeckoLib bereits vorbereitet!)
        // =====================================================================
        poseStack.pushPose();
        
        // Translation anwenden
        poseStack.translate(x, y - 0.25F, z);

        // Rotationen keck auf die Achsen mappen
        poseStack.mulPose(Axis.XP.rotationDegrees(rx));
        poseStack.mulPose(Axis.YP.rotationDegrees(ry));
        poseStack.mulPose(Axis.ZP.rotationDegrees(rz));
        
        // Skalierung anwenden (Inklusive Vanilla-Korrektur: 0.625 und Y/Z Achsen-Flip!)
        poseStack.scale(0.625F * scaleX, -0.625F * scaleY, -0.625F * scaleZ);

        // Schreibt das Hutmodell als echtes 3D-Modell auf den Bildschirm
        Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(
            animatable, stack, ItemDisplayContext.HEAD, false, poseStack, bufferSource, packedLight
        );

        poseStack.popPose();
    }
}