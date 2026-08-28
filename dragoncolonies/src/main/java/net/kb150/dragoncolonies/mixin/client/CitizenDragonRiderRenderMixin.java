package net.kb150.dragoncolonies.mixin.client;

import com.minecolonies.api.client.render.modeltype.CitizenModel;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.client.render.RenderBipedCitizen;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.magister.bookofdragons.client.render.RiderMatrixDataflow;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.bookofdragons.entity.data.DragonRiderConfig;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderBipedCitizen.class, remap = false)
public abstract class CitizenDragonRiderRenderMixin {

    @Inject(
        method = "render(Lcom/minecolonies/api/entity/citizen/AbstractEntityCitizen;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("HEAD")
    )
    private void dragoncolonies$transformCitizenOnDragon(
        AbstractEntityCitizen citizen,
        float limbSwing,
        float partialTicks,
        PoseStack matrixStack,
        MultiBufferSource renderTypeBuffer,
        int light,
        CallbackInfo ci
    ) {
        if (citizen.getVehicle() instanceof DragonBase dragon) {
            int dragonId = dragon.getId();
            // 1. Live-Matrix aus RiderMatrixDataflow abfragen
            Matrix4f viewMatrix = RiderMatrixDataflow.get(dragonId);
            if (viewMatrix == null) {
                return;
            }

            long lastUpdate = RiderMatrixDataflow.getTimestamp(dragonId);
            long age = System.currentTimeMillis() - lastUpdate;
            if (age > 200L) {
                return;
            }

            // 2. Offsets aus der Drachen-Config
            DragonRiderConfig.RiderPosition riderConfig = DragonRiderConfig.getRiderPosition(dragon);
            float seatOffsetX = riderConfig.offset.x();
            float seatOffsetY = riderConfig.offset.y();
            float seatOffsetZ = riderConfig.offset.z();

            Matrix4f citizenMatrix = new Matrix4f(viewMatrix);
            citizenMatrix.normalize3x3();

            if (Float.isFinite(citizenMatrix.m00()) && Float.isFinite(citizenMatrix.m11()) && Float.isFinite(citizenMatrix.m22())) {
                // 3. Matrix auf den PoseStack übertragen
                matrixStack.last().pose().set(citizenMatrix);
                matrixStack.translate(seatOffsetX, seatOffsetY, seatOffsetZ);

                float dragonYaw = Mth.rotLerp(partialTicks, dragon.yBodyRotO, dragon.yBodyRot);
                matrixStack.mulPose(Axis.YP.rotationDegrees(dragonYaw - 180.0F));

                RiderMatrixDataflow.notifyRendered(dragonId);
            }
        }
    }

    @Inject(
        method = "setupMainModelFrom",
        at = @At("RETURN")
    )
    private void dragoncolonies$preventSittingPose(AbstractEntityCitizen citizen, CallbackInfo ci) {
        if (citizen.getVehicle() instanceof DragonBase) {
            RenderBipedCitizen renderer = (RenderBipedCitizen) (Object) this;
            CitizenModel<?> model = renderer.getModel();
            if (model != null) {
                model.riding = false;
            }
        }
    }
}