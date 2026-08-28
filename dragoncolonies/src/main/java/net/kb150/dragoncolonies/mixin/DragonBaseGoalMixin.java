
package net.kb150.dragoncolonies.mixin;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.magister.bookofdragons.entity.ai.goal.DragonBaseGoal;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.bookofdragons.entity.state.DragonStateContext;
import net.magister.bookofdragons.entity.state.GroundStance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entsperrt Book of Dragons Angriffs-Goals (DragonAttackGoal), 
 * wenn eine Minecolonies-Wache auf dem Drachen reitet!
 * Verhindert außerdem, dass verwaiste Drachen sinnlose Dinge tun,
 * während sie versuchen, nach Hause zu fliegen.
 */
@Mixin(value = DragonBaseGoal.class, remap = false)
public abstract class DragonBaseGoalMixin {

    @Shadow protected DragonBase dragon;

    @Inject(method = {"canUse", "m_8036_"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void dragoncolonies$allowAttackGoalsForCitizen(CallbackInfoReturnable<Boolean> cir) {
        if (this.dragon != null) {
            
            // --- NEU: Blockiere Vanilla/BoD KI-Ziele, wenn der Drache verwaist ist und heim fliegen soll ---
            if (this.dragon.getPersistentData().contains("DragonColonies_OrphanTicks") && 
                this.dragon.getPersistentData().getBoolean("DragonColonies_GuardDeployed")) {
                cir.setReturnValue(false);
                return;
            }

            // --- BESTEHEND: Entsperre Angriffs-Goals für Wachen-Reiter ---
            if (this.dragon.getFirstPassenger() instanceof AbstractEntityCitizen) {
                String goalName = this.getClass().getSimpleName();
                
                // WHITELIST: Alle Angriffs-Goals von BoD entsperren!
                if (goalName.contains("Attack") || goalName.contains("Prowl") || goalName.contains("Strafe")) {
                    DragonStateContext context = this.dragon.getStateContext();
                    boolean canUse = this.dragon.getCommand() != 1 
                        && context.getGroundStance() != GroundStance.INCAPACITATED 
                        && context.getGroundStance() != GroundStance.SLEEPING 
                        && context.getGroundStance() != GroundStance.SITTING;
                    
                    cir.setReturnValue(canUse); // TRUE zurückgeben (isVehicle-Sperre umgehen!)
                    return;
                }
                
                // Alle wilden Nicht-Kampf-Goals (Wandern, Rest etc.) weiterhin blockieren
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = {"canContinueToUse", "m_8045_"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void dragoncolonies$allowAttackGoalsContinueForCitizen(CallbackInfoReturnable<Boolean> cir) {
        if (this.dragon != null) {

            // --- NEU: Blockiere Fortsetzung von Zielen, wenn verwaist ---
            if (this.dragon.getPersistentData().contains("DragonColonies_OrphanTicks") && 
                this.dragon.getPersistentData().getBoolean("DragonColonies_GuardDeployed")) {
                cir.setReturnValue(false);
                return;
            }

            // --- BESTEHEND: Entsperre Fortsetzung für Wachen-Reiter ---
            if (this.dragon.getFirstPassenger() instanceof AbstractEntityCitizen) {
                String goalName = this.getClass().getSimpleName();
                
                if (goalName.contains("Attack") || goalName.contains("Prowl") || 
					goalName.contains("Strafe") || goalName.contains("Target") || 
					goalName.contains("Combat") || goalName.contains("Approach") || 
					goalName.contains("Charge")) {
					// BoD-Goal gewähren

                    DragonStateContext context = this.dragon.getStateContext();
                    boolean canContinue = this.dragon.getCommand() != 1 
                        && context.getGroundStance() != GroundStance.INCAPACITATED 
                        && context.getGroundStance() != GroundStance.SLEEPING 
                        && context.getGroundStance() != GroundStance.SITTING;
                    
                    cir.setReturnValue(canContinue);
                    return;
                }
                
                cir.setReturnValue(false);
            }
        }
    }
}