package net.kb150.dragoncolonies.mixin;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.magister.bookofdragons.entity.ai.movement.AIMovementComponent;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AIMovementComponent.class, remap = false)
public abstract class AIMovementComponentMixin {

    // ===================================================================================
    // WATCHDOG (Schutz vor Async-Deadlocks in Book of Dragons)
    // ===================================================================================

    @Unique
    private static final int dragoncolonies$CALCULATING_WATCHDOG_TICKS = 100;

    @Unique
    private int dragoncolonies$calculatingTicks = 0;

    @Inject(method = "serverTick", at = @At("TAIL"), remap = false, require = 0)
    private void dragoncolonies$watchdogStuckCalculating(CallbackInfo ci) {
        AIMovementComponent self = (AIMovementComponent) (Object) this;

        if (self.getState() == AIMovementComponent.PathState.CALCULATING) {
            this.dragoncolonies$calculatingTicks++;
            if (this.dragoncolonies$calculatingTicks > dragoncolonies$CALCULATING_WATCHDOG_TICKS) {
                this.dragoncolonies$calculatingTicks = 0;
                // Löst den regulären Stuck-Mechanismus aus (RETRY_FLY / RETRY_SAME / FAILED)
                self.handleStuck();
            }
        } else {
            this.dragoncolonies$calculatingTicks = 0;
        }
    }

    // ===================================================================================
    // RIDER KI UNLOCK (Verhindert, dass die Mod wegen eines Passagiers auf 0 bremst)
    // ===================================================================================

    /**
     * Fängt die Abfrage "this.host.m_20160_()" in der serverTick() der Mod ab.
     * Wenn ein Minecolonies-Bürger den Drachen reitet, sagen wir der Mod:
     * "Hier sitzt kein Spieler, lass deine eigene KI weiterlaufen!"
     */
    @Redirect(
        method = "serverTick",
        at = @At(value = "INVOKE", target = "Lnet/magister/bookofdragons/entity/base/dragon/DragonBase;m_20160_()Z", remap = false),
        remap = false,
        require = 0
    )
    private boolean dragoncolonies$fakeNotVehicleForDragonBase(DragonBase instance) {
        if (instance.getFirstPassenger() instanceof AbstractEntityCitizen) {
            return false; // KI entsperren
        }
        return instance.isVehicle(); // Normales Vanilla-Verhalten
    }

    /**
     * Fallback: Falls Forge den Bytecode so auflöst, dass die Methode der Entity-Klasse gehört.
     */
    @Redirect(
        method = "serverTick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;m_20160_()Z", remap = false),
        remap = false,
        require = 0
    )
    private boolean dragoncolonies$fakeNotVehicleForEntity(DragonBase instance) {
        if (instance.getFirstPassenger() instanceof AbstractEntityCitizen) {
            return false; // KI entsperren
        }
        return instance.isVehicle();
    }
}