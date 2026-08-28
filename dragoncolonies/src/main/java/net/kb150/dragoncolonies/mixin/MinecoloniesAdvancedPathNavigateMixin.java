package net.kb150.dragoncolonies.mixin;

import com.minecolonies.core.entity.pathfinding.navigation.AbstractAdvancedPathNavigate;
import com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate;
import com.minecolonies.core.entity.pathfinding.pathjobs.AbstractPathJob;
import com.minecolonies.core.entity.pathfinding.pathresults.PathResult;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin für MinecoloniesAdvancedPathNavigate.
 * Blockiert das Erstellen von Boden-Pfadjobs sowie das automatische Absteigen (stopRiding) 
 * und Löschen (discard) von Drachenreitern durch den Minecolonies-Pfadfinder.
 */
@Mixin(value = MinecoloniesAdvancedPathNavigate.class, remap = false)
public abstract class MinecoloniesAdvancedPathNavigateMixin extends AbstractAdvancedPathNavigate {

    public MinecoloniesAdvancedPathNavigateMixin(Mob entity, Level world) {
        super(entity, world);
    }

    /**
     * WURZEL-LÖSUNG: Blockiert die Erstellung von Boden-Pfadjobs, solange die Wache fliegt.
     * Ohne aktiven Pfadjob wertet der PathingStuckHandler die Bewegung nicht als "festgesteckt".
     */
    @Inject(
        method = "setPathJob",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private <T extends AbstractPathJob> void dragoncolonies$preventPathJobWhileMounted(
            AbstractPathJob job, BlockPos dest, double speedFactor, boolean safeDestination, 
            CallbackInfoReturnable<PathResult<T>> cir) {
        
        if (this.ourEntity != null && this.ourEntity.isPassenger() && this.ourEntity.getVehicle() instanceof DragonBase) {
            cir.setReturnValue(null);
        }
    }

    /**
     * Verhindert das erzwungene Absteigen des Bürgers vom Drachen bei Pfad-Updates/Stopps.
     */
    @Redirect(
        method = "*",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Mob;m_8127_()V"
        ),
        remap = false,
        require = 0
    )
    private void dragoncolonies$preventCitizenDismountSrg(Mob mob) {
        if (mob.getVehicle() instanceof DragonBase) {
            return;
        }
        mob.stopRiding();
    }

    @Redirect(
        method = "*",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Mob;stopRiding()V"
        ),
        remap = false,
        require = 0
    )
    private void dragoncolonies$preventCitizenDismountMojang(Mob mob) {
        if (mob.getVehicle() instanceof DragonBase) {
            return;
        }
        mob.stopRiding();
    }

    @Redirect(
        method = "*",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;m_8127_()V"
        ),
        remap = false,
        require = 0
    )
    private void dragoncolonies$preventCitizenDismountEntitySrg(Entity entity) {
        if (entity.getVehicle() instanceof DragonBase) {
            return;
        }
        entity.stopRiding();
    }

    @Redirect(
        method = "*",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;stopRiding()V"
        ),
        remap = false,
        require = 0
    )
    private void dragoncolonies$preventCitizenDismountEntityMojang(Entity entity) {
        if (entity.getVehicle() instanceof DragonBase) {
            return;
        }
        entity.stopRiding();
    }

    /**
     * Verhindert das automatische Löschen des Drachens.
     */
    @Redirect(
        method = "*",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;m_142687_(Lnet/minecraft/world/entity/Entity$RemovalReason;)V"
        ),
        remap = false,
        require = 0
    )
    private void dragoncolonies$preventDragonDiscardInNavigation(Entity entity, Entity.RemovalReason reason) {
        if (entity instanceof DragonBase) {
            return;
        }
        entity.remove(reason);
    }

    @Redirect(
        method = "*",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V"
        ),
        remap = false,
        require = 0
    )
    private void dragoncolonies$preventDragonRemoveInNavigation(Entity entity, Entity.RemovalReason reason) {
        if (entity instanceof DragonBase) {
            return;
        }
        entity.remove(reason);
    }
}