package net.kb150.dragoncolonies.mixin;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.other.AbstractFastMinecoloniesEntity;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import net.kb150.dragoncolonies.ai.DragonNavigationHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityNavigationUtils.class, remap = false)
public class EntityNavigationUtilsMixin {

    @Inject(method = "walkToPos(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/core/BlockPos;IZD)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private static <T extends Mob> void dragoncolonies$interceptWalkToPos(T entity, BlockPos desiredPosition, int distToDesired, boolean safeDestination, double speedFactor, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof AbstractEntityCitizen citizen) {
            Boolean flightResult = DragonNavigationHandler.handleDragonFlight(citizen, desiredPosition, distToDesired);
            // GANZ WICHTIG: Nur wenn flightResult NICHT null ist, brechen wir Minecolonies ab!
            if (flightResult != null) {
                cir.setReturnValue(flightResult);
            }
            // Wenn flightResult == null ist, läuft Minecolonies Vanilla einfach weiter!
        }
    }

    @Inject(method = "walkCloseToXNearY(Lcom/minecolonies/api/entity/other/AbstractFastMinecoloniesEntity;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;IZD)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dragoncolonies$interceptWalkCloseToXNearY(AbstractFastMinecoloniesEntity entity, BlockPos desiredPosition, BlockPos nearbyPosition, int distToDesired, boolean safeDestination, double speedFactor, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof AbstractEntityCitizen citizen) {
            Boolean flightResult = DragonNavigationHandler.handleDragonFlight(citizen, desiredPosition, distToDesired);
            if (flightResult != null) {
                cir.setReturnValue(flightResult);
            }
        }
    }
}