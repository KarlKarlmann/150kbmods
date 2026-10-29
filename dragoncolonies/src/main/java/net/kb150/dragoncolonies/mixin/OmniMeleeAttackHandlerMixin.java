package net.kb150.dragoncolonies.mixin;

import net.magister.liberanimalium.chassis.FaunaBase;
import net.magister.liberanimalium.chassis.component.melee.OmniMeleeAttackHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Filtert alle befreundeten Ziele (Passagiere, Kolonisten, befreundete Drachen)
 * vollstaendig aus der Nahkampf-Engine von FAUNA heraus.
 */
@Mixin(value = OmniMeleeAttackHandler.class, remap = false)
public abstract class OmniMeleeAttackHandlerMixin {

    @Shadow private FaunaBase body;
    @Shadow private LivingEntity victim;

    // Verhindert das Einleiten eines Nahkampfangriffs, wenn das Ziel befreundet ist
    @Inject(method = "begin", at = @At("HEAD"), cancellable = true, remap = false)
    private void dragoncolonies$neverBeginOnFriendly(LivingEntity intended, boolean play, CallbackInfoReturnable<Boolean> cir) {
        if (intended != null && this.body != null && this.body.isFriendlyTarget(intended)) {
            cir.setReturnValue(false);
        }
    }

    // Setzt das Opfer vor der Trefferberechnung zurueck, falls es befreundet ist
    @Inject(method = "dealHit", at = @At("HEAD"), remap = false)
    private void dragoncolonies$clearVictimIfFriendly(CallbackInfoReturnable<Boolean> cir) {
        if (this.victim != null && this.body != null && this.body.isFriendlyTarget(this.victim)) {
            this.victim = null;
        }
    }

    // Filtert alle befreundeten Entitaeten aus der Treffer-Kandidatenliste heraus
    @ModifyVariable(method = "dealHit", at = @At("STORE"), ordinal = 0, remap = false)
    private List<Entity> dragoncolonies$filterFriendlyCandidates(List<Entity> candidates) {
        if (candidates == null || candidates.isEmpty() || this.body == null) {
            return candidates;
        }

        candidates.removeIf(entity -> entity != null && this.body.isFriendlyTarget(entity));
        return candidates;
    }
}