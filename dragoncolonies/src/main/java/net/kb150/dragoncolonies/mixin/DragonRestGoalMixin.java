package net.kb150.dragoncolonies.mixin;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.magister.bookofdragons.entity.ai.goal.DragonRestGoal;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin für DragonRestGoal.
 * Verhindert, dass der Drache in den Schlaf/Sitz-Modus wechselt, während eine Minecolonies-Wache ihn reitet.
 */
@Mixin(value = DragonRestGoal.class, remap = false)
public abstract class DragonRestGoalMixin {

    // Shadow erlaubt uns direkten Zugriff auf das private Feld 'dragon' der Zielklasse!
    @Shadow(remap = false)
    private DragonBase dragon;

    @Inject(method = {"canUse", "m_8036_"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void dragoncolonies$blockRestForCitizenRider(CallbackInfoReturnable<Boolean> cir) {
        if (this.dragon != null && this.dragon.getFirstPassenger() instanceof AbstractEntityCitizen) {
            cir.setReturnValue(false); // Verhindert das Starten des Ziels
        }
    }

    @Inject(method = {"canContinueToUse", "m_8045_"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void dragoncolonies$stopRestForCitizenRider(CallbackInfoReturnable<Boolean> cir) {
        if (this.dragon != null && this.dragon.getFirstPassenger() instanceof AbstractEntityCitizen) {
            cir.setReturnValue(false); // Bricht das Ziel ab, falls es bereits läuft
        }
    }
}