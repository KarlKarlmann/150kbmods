package net.kb150.dragoncolonies.mixin;

import com.minecolonies.api.entity.citizen.AbstractCivilianEntity;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hebelt Minecolonies' Typen-Prüfung in AbstractCivilianEntity aus
 * und führt das echte Vanilla-Aufsteigen über super.startRiding aus.
 */
@Mixin(AbstractCivilianEntity.class)
public abstract class AbstractEntityCitizenMixin extends Entity {

    public AbstractEntityCitizenMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    /* STREAMING_CHUNK:Injecting super.startRiding bypass... */
    @Inject(
        method = {"m_7998_", "startRiding"},
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void dragoncolonies$allowDragonRiding(Entity entity, boolean force, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof DragonBase) {
            // Echter Aufruf der Vanilla Entity#startRiding Methode (kompiliert sauber im Dev-Workspace)
            boolean result = super.startRiding(entity, force);
            //System.out.println("[DragonColonies-Mixin] Echter Vanilla-Aufstieg ausgeführt! Ergebnis: " + result + " | isPassenger: " + this.isPassenger());
            cir.setReturnValue(result);
        }
    }
}