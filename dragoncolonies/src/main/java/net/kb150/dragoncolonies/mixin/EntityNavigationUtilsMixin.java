package net.kb150.dragoncolonies.mixin;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.other.AbstractFastMinecoloniesEntity;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import net.kb150.dragoncolonies.DragonColonies;
import net.kb150.dragoncolonies.ai.AbstractEntityAIDragonRider;
import net.kb150.dragoncolonies.ai.DragonNavigationHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityNavigationUtils.class, remap = false)
public class EntityNavigationUtilsMixin {

    private static final boolean DEBUG_NAVIGATION = true;

    @Inject(
            method = "walkToPos(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/core/BlockPos;IZD)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static <T extends Mob> void dragoncolonies$interceptWalkToPos(
            T entity,
            BlockPos desiredPosition,
            int distToDesired,
            boolean safeDestination,
            double speedFactor,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!(entity instanceof AbstractEntityCitizen citizen)) {
            return;
        }

        // Nur Dragon-Rider-Jobs dürfen unsere Flugnavigation benutzen.
        AbstractEntityAIDragonRider<?, ?> rider =
                AbstractEntityAIDragonRider.getRiderForCitizen(citizen);

        if (rider == null) {
            return;
        }

        logNavigationRequest(
                "walkToPos",
                citizen,
                desiredPosition,
                distToDesired,
                safeDestination,
                speedFactor
        );

        Boolean flightResult = DragonNavigationHandler.handleDragonFlight(
                citizen,
                desiredPosition,
                distToDesired
        );

        if (flightResult != null) {
            cir.setReturnValue(flightResult);
        }
    }

    @Inject(
            method = "walkCloseToXNearY(Lcom/minecolonies/api/entity/other/AbstractFastMinecoloniesEntity;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;IZD)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void dragoncolonies$interceptWalkCloseToXNearY(
            AbstractFastMinecoloniesEntity entity,
            BlockPos desiredPosition,
            BlockPos nearbyPosition,
            int distToDesired,
            boolean safeDestination,
            double speedFactor,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!(entity instanceof AbstractEntityCitizen citizen)) {
            return;
        }

        // Nur Dragon-Rider-Jobs dürfen unsere Flugnavigation benutzen.
        AbstractEntityAIDragonRider<?, ?> rider =
                AbstractEntityAIDragonRider.getRiderForCitizen(citizen);

        if (rider == null) {
            return;
        }

        logNavigationRequest(
                "walkCloseToXNearY",
                citizen,
                desiredPosition,
                distToDesired,
                safeDestination,
                speedFactor
        );

        Boolean flightResult = DragonNavigationHandler.handleDragonFlight(
                citizen,
                desiredPosition,
                distToDesired
        );

        if (flightResult != null) {
            cir.setReturnValue(flightResult);
        }
    }

    private static void logNavigationRequest(
            String source,
            AbstractEntityCitizen citizen,
            BlockPos desiredPosition,
            int distToDesired,
            boolean safeDestination,
            double speedFactor
    ) {
        if (!DEBUG_NAVIGATION) {
            return;
        }

        String vehicle = citizen.getVehicle() != null
                ? citizen.getVehicle().getType().getDescriptionId()
                : "NONE";

        DragonColonies.LOGGER.info(
                "[DRAGON-NAV-REQUEST] source={} | citizen={} | target={} | dist={} | safe={} | speed={} | vehicle={}",
                source,
                citizen.getName().getString(),
                desiredPosition.toShortString(),
                distToDesired,
                safeDestination,
                speedFactor,
                vehicle
        );

        StackTraceElement[] trace = Thread.currentThread().getStackTrace();

        for (int i = 2; i < Math.min(trace.length, 16); i++) {
            StackTraceElement element = trace[i];

            String className = element.getClassName();

            if (className.equals(Thread.class.getName())) {
                continue;
            }

            if (className.equals(EntityNavigationUtils.class.getName())) {
                continue;
            }

            DragonColonies.LOGGER.info(
                    "    <- {}.{}:{}",
                    className,
                    element.getMethodName(),
                    element.getLineNumber()
            );
        }
    }
}