package net.kb150.woerp.client.compat.epicfight;

import com.mojang.blaze3d.vertex.PoseStack;
import net.kb150.woerp.Woerp;
import net.kb150.woerp.client.layer.MutationLayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import yesman.epicfight.api.client.forgeevent.PatchedRenderersEvent;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;
import yesman.epicfight.client.renderer.patched.layer.PatchedLayer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

public class EpicFightCompat {

    @SubscribeEvent
    public static void onEpicFightRenderers(PatchedRenderersEvent.Modify event) {
        int count = 0;
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES) {
            var renderer = event.get(type);
            if (renderer instanceof PatchedLivingEntityRenderer patchedLiving) {
                // Zwingt Epic Fights Render-Loop, den Vanilla-Layer waehrend Kampfanimationen nicht zu verwerfen
                patchedLiving.addPatchedLayerAlways(
                    MutationLayer.class,
                    new EpicFightMutationLayer()
                );
                count++;
            }
        }
        Woerp.LOGGER.info("[WOERP-EF] MutationLayer erfolgreich in {} EpicFight-Renderer eingeklinkt.", count);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static class EpicFightMutationLayer extends PatchedLayer {
        @Override
        protected void renderLayer(LivingEntityPatch entitypatch, LivingEntity entityliving, RenderLayer vanillaLayer, PoseStack poseStack, MultiBufferSource buffer, int packedLight, OpenMatrix4f[] poses, float bob, float yRot, float xRot, float partialTicks) {
            if (entityliving.isInvisible() || vanillaLayer == null) return;

            // Reicht den Aufruf direkt an unsere bestehende ModelPart-Pipeline durch
            vanillaLayer.render(poseStack, buffer, packedLight, entityliving, 0.0F, 0.0F, partialTicks, bob, yRot, xRot);
        }
    }
}