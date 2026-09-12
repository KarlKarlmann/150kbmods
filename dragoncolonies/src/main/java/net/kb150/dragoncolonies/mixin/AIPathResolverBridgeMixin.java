package net.kb150.dragoncolonies.mixin;

import net.kb150.dragoncolonies.ai.BoDPathInfo;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;

/**
 * Liest nur das Ergebnis des bestehenden BoD-Pathfinders mit.
 * Die eigentliche Navigation bleibt vollstaendig bei BoD.
 */
@Mixin(
        targets = "net.magister.bookofdragons.entity.ai.movement.AIPathResolver",
        remap = false
)
public abstract class AIPathResolverBridgeMixin {

    @Redirect(
            method = {"startFlyingPathAsync", "attemptGroundPathAsync"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/magister/bookofdragons/entity/ai/pathfinding/DragonAsyncPathfinder;calculatePathAsync(Lnet/magister/bookofdragons/entity/base/dragon/DragonBase;Lnet/minecraft/world/phys/Vec3;ZLjava/util/function/Consumer;)V",
                    remap = false
            ),
            remap = false
    )
    private void dragoncolonies$captureBoDPath(
            DragonBase dragon,
            Vec3 target,
            boolean isFlying,
            Consumer<Path> callback
    ) {
        BoDPathInfo.begin(dragon, target);

        net.magister.bookofdragons.entity.ai.pathfinding.DragonAsyncPathfinder.calculatePathAsync(
                dragon,
                target,
                isFlying,
                path -> {
                    BoDPathInfo.record(dragon, target, path);
                    callback.accept(path);
                }
        );
    }
}
