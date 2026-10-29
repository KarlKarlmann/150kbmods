package net.kb150.dragoncolonies.mixin.client;

import com.minecolonies.api.client.render.modeltype.CitizenModel;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.client.render.RenderBipedCitizen;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.liberanimalium.render.RiderMatrixDataflow;
import net.magister.liberanimalium.ride.render.RiderAnimator;
import net.magister.liberanimalium.ride.render.RiderPoseProfile;
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

            // 1. Live-Matrix aus FAUNA RiderMatrixDataflow abfragen
            Matrix4f viewMatrix = RiderMatrixDataflow.get(dragonId);
            if (viewMatrix == null) {
                return;
            }

            long lastUpdate = RiderMatrixDataflow.getTimestamp(dragonId);
            long age = System.currentTimeMillis() - lastUpdate;
            if (age > 200L) {
                return;
            }

            // 2. Offsets aus dem FAUNA Rider-System abfragen
            RiderPoseProfile.Seat seat = RiderAnimator.seat(dragon);
            float seatOffsetX = seat != null ? seat.x() : 0.0F;
            float seatOffsetY = seat != null ? seat.y() : 0.0F;
            float seatOffsetZ = seat != null ? seat.z() : 0.0F;

            // 3. FAUNA Relativ-Vektor: Distanz zwischen Buerger und Drachen-Mitte aufholen
            float toBodyX = (float) (Mth.lerp(partialTicks, dragon.xo, dragon.getX()) - Mth.lerp(partialTicks, citizen.xo, citizen.getX()));
            float toBodyY = (float) (Mth.lerp(partialTicks, dragon.yo, dragon.getY()) - Mth.lerp(partialTicks, citizen.yo, citizen.getY()));
            float toBodyZ = (float) (Mth.lerp(partialTicks, dragon.zo, dragon.getZ()) - Mth.lerp(partialTicks, citizen.zo, citizen.getZ()));

            // 4. Matrix mit Kamera-Stack des Buergers verrechnen
            Matrix4f citizenMatrix = (new Matrix4f(matrixStack.last().pose()))
                    .translate(toBodyX, toBodyY, toBodyZ)
                    .mul(viewMatrix);
            citizenMatrix.normalize3x3();

            if (Float.isFinite(citizenMatrix.m00()) && Float.isFinite(citizenMatrix.m11()) && Float.isFinite(citizenMatrix.m22())) {
                matrixStack.last().pose().set(citizenMatrix);
                matrixStack.translate(seatOffsetX, seatOffsetY, seatOffsetZ);

                // Dynamische Koerperdrehung relativ zum Drachen
                float head = Mth.rotLerp(partialTicks, citizen.yHeadRotO, citizen.yHeadRot);
                float vehicle = Mth.rotLerp(partialTicks, dragon.yBodyRotO, dragon.yBodyRot);
                float offset = Mth.clamp(Mth.wrapDegrees(head - vehicle), -85.0F, 85.0F);
                float riderBody = head - offset;
                if (offset * offset > 2500.0F) {
                    riderBody += offset * 0.2F;
                }

                matrixStack.mulPose(Axis.YP.rotationDegrees(riderBody - 180.0F));

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