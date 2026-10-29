package net.kb150.dragoncolonies.mixin;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.kb150.dragoncolonies.util.ColonyRelationHelper;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.liberanimalium.chassis.FaunaBase;
import net.magister.liberanimalium.fauna.api.Kind;
import net.magister.liberanimalium.fauna.core.FaunaMind;
import net.magister.liberanimalium.fauna.core.FaunaTicker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import java.util.UUID;

/**
 * Verhindert auf FAUNA-Chassis-Ebene jegliche Aggression gegen Passagiere und Kolonisten
 * und definiert Kolonie-Mitglieder als befreundete Ziele (isFriendlyTarget).
 */
@Mixin(value = FaunaBase.class, remap = false)
public abstract class FaunaBaseMixin {

    // 0. Wurzel-Schutz: Passagiere und Kolonisten gelten fuer FAUNA als selbe Spezies (keine Beute/EATS)
    @Inject(method = "kindOf", at = @At("HEAD"), cancellable = true, remap = false)
    private void dragoncolonies$friendlyKindOf(Entity other, CallbackInfoReturnable<Kind> cir) {
        if (other == null) return;
        FaunaBase self = (FaunaBase) (Object) this;

        boolean isFriend = self.hasPassenger(other) || self.getPassengers().contains(other);

        if (!isFriend && self instanceof DragonBase dragon) {
            if (other instanceof AbstractEntityCitizen citizen && ColonyRelationHelper.isSameColony(citizen, dragon)) {
                isFriend = true;
            } else if (other instanceof DragonBase otherDragon && ColonyRelationHelper.isSameColony(dragon, otherDragon)) {
                isFriend = true;
            } else if (other instanceof Player player && ColonyRelationHelper.isPlayerInColony(player, dragon)) {
                isFriend = true;
            }
        }

        if (isFriend) {
            cir.setReturnValue(new Kind(other.getEncodeId(), Set.of("pack", "friendly"), true));
        }
    }

    // 1. Single Source of Truth fuer Freund-Erkennung in FAUNA
    @Inject(method = "isFriendlyTarget", at = @At("HEAD"), cancellable = true, remap = false)
    private void dragoncolonies$isFriendlyTarget(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (target == null) return;
        FaunaBase self = (FaunaBase) (Object) this;

        // Regel A: Jeder Passagier auf dem Ruecken ist ein Verbuendeter
        if (self.hasPassenger(target) || self.getPassengers().contains(target)) {
            cir.setReturnValue(true);
            return;
        }

        // Regel B: Kolonie-Verbuendete (Siedler, andere Drachen, Kolonie-Spieler)
        if (self instanceof DragonBase dragon) {
            if (target instanceof AbstractEntityCitizen citizen) {
                if (ColonyRelationHelper.isSameColony(citizen, dragon)) {
                    cir.setReturnValue(true);
                    return;
                }
            }

            if (target instanceof DragonBase otherDragon) {
                if (ColonyRelationHelper.isSameColony(dragon, otherDragon)) {
                    cir.setReturnValue(true);
                    return;
                }
            }

            if (target instanceof Player player) {
                if (ColonyRelationHelper.isPlayerInColony(player, dragon)) {
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }

    // 2. FAUNA spares() verschont alle befreundeten Ziele
    @Inject(method = "spares", at = @At("HEAD"), cancellable = true, remap = false)
    private void dragoncolonies$spares(LivingEntity other, CallbackInfoReturnable<Boolean> cir) {
        if (other == null) return;
        FaunaBase self = (FaunaBase) (Object) this;

        if (self.isFriendlyTarget(other)) {
            cir.setReturnValue(true);
        }
    }

    // 3. Verhindert, dass FAUNAs Nahkampf-Engine einen Angriff gegen Freunde startet
    @Inject(method = "swingAt", at = @At("HEAD"), cancellable = true, remap = false)
    private void dragoncolonies$neverSwingAtFriendly(UUID targetId, boolean inPlay, CallbackInfoReturnable<Boolean> cir) {
        FaunaBase self = (FaunaBase) (Object) this;

        for (Entity passenger : self.getPassengers()) {
            if (passenger.getUUID().equals(targetId)) {
                cir.setReturnValue(false);
                return;
            }
        }

        if (self.level() instanceof ServerLevel serverLevel) {
            Entity target = serverLevel.getEntity(targetId);
            if (target != null && self.isFriendlyTarget(target)) {
                cir.setReturnValue(false);
            }
        }
    }

    // 4. Verhindert, dass Vanilla/FAUNA setTarget Freunde anvisiert
    @Inject(method = {"setTarget", "m_6710_"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void dragoncolonies$neverTargetFriendly(LivingEntity target, CallbackInfo ci) {
        if (target == null) return;
        FaunaBase self = (FaunaBase) (Object) this;

        if (self.isFriendlyTarget(target)) {
            ci.cancel();
        }
    }

    // 5. Blockiert physischen Nahkampfschaden gegen Freunde
    @Inject(method = {"doHurtTarget", "m_7327_"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void dragoncolonies$neverHurtFriendly(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (target == null) return;
        FaunaBase self = (FaunaBase) (Object) this;

        if (self.isFriendlyTarget(target)) {
            cir.setReturnValue(false);
        }
    }

    // 6. Resettet sofort jedes versehentlich gesetzte feindliche Ziel auf befreundete Entitaeten
    @Inject(method = {"aiStep", "m_8107_"}, at = @At("HEAD"), remap = false, require = 0)
    private void dragoncolonies$clearFriendlyTarget(CallbackInfo ci) {
        FaunaBase self = (FaunaBase) (Object) this;
        if (self.getTarget() != null && self.isFriendlyTarget(self.getTarget())) {
            self.setTarget(null);
        }
    }

    // 7. Stummschalten von FaunaTicker waehrend eine Wache im Sattel sitzt:
    // Unterdrueckt autonomen Eigenwillen (Wandern, Notlandungs-Flucht in die Ferne, etc.)
    @Redirect(
        method = {"customServerAiStep", "m_8024_"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/magister/liberanimalium/fauna/core/FaunaTicker;tick(Lnet/magister/liberanimalium/fauna/core/FaunaMind;)V"
        ),
        remap = false,
        require = 0
    )
    private void dragoncolonies$silenceFaunaTickerWhileMounted(FaunaMind mind) {
        FaunaBase self = (FaunaBase) (Object) this;
        if (self.getFirstPassenger() instanceof AbstractEntityCitizen) {
            return;
        }
        FaunaTicker.tick(mind);
    }
}