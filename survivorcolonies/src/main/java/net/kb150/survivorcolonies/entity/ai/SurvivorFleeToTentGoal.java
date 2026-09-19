package net.kb150.survivorcolonies.entity.ai;

import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Höchste Bewegungs-Priorität im Goal-Selector (noch vor Avoid/Melee!): Sobald überhaupt
 * eine Fluchtreaktion fällig wäre (wenig Leben ODER Gegner stärker - dieselbe Bedingung wie
 * bei AvoidEntityGoal), rennt der Survivor GEZIELT zum Zelt statt planlos in die Wildnis.
 * "Ist er erstmal irgendwo im Nirgendwo, hat er eh verloren" - AvoidEntityGoal bleibt daher
 * nur noch als Fallback aktiv, falls gerade kein Zelt existiert (siehe dessen Bedingung).
 *
 * Drinnen wird er unsichtbar (spart uns Kollisions-/Clipping-Ärger mit der niedrigen
 * Zelt-Decke) und bleibt so lange versteckt, bis eine kurze Ruhephase vergangen ist.
 *
 * Da normale Monster (2 Blöcke hoch) körperlich gar nicht ins 1-Block-hohe Zelt passen,
 * reicht "drinnen sein" bereits als Schutz - ein explizites Ziel-Löschen bei den Angreifern
 * ist nicht nötig, die geben laut vanilla-KI von selbst auf, wenn sie ihn nicht erreichen.
 */
public class SurvivorFleeToTentGoal extends Goal {
    private final SurvivorEntity survivor;

    private boolean isHiding = false;
    private int safeTicks = 0;

    private static final int SAFE_TICKS_BEFORE_EXIT = 60; // ~3 Sekunden Ruhe, bevor er rauskommt

    public SurvivorFleeToTentGoal(SurvivorEntity survivor) {
        this.survivor = survivor;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (this.isHiding) return false; // läuft schon, siehe canContinueToUse()

        BlockPos anchor = getTentAnchor();
        return anchor != null && isInDanger();
    }

    /** Nutzt dieselbe persönlichkeitsbasierte Schwelle wie der AvoidEntityGoal-Fallback. */
    private boolean isInDanger() {
        return this.survivor.wantsToFleeFromTarget();
    }

    @Override
    public void start() {
        this.isHiding = false;
        this.safeTicks = 0;
    }

    @Override
    public void tick() {
        BlockPos anchor = getTentAnchor();
        if (anchor == null) return; // Zelt inzwischen weg -> nichts zu tun, canContinueToUse beendet das gleich

        if (!this.isHiding) {
            this.survivor.getNavigation().moveTo(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D, 1.1D);

            if (this.survivor.distanceToSqr(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D) < 0.6D) {
                this.survivor.getNavigation().stop();
                this.survivor.setPos(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D);
                this.survivor.setHiddenInTent(true);
                this.isHiding = true;
                this.safeTicks = 0;
            }
        } else {
            boolean stillInDanger = isInDanger();
            this.safeTicks = stillInDanger ? 0 : this.safeTicks + 1;
        }
    }

    @Override
    public boolean canContinueToUse() {
        BlockPos anchor = getTentAnchor();
        if (anchor == null) return false; // Zelt weg -> raus damit

        if (!this.isHiding) return true; // noch auf dem Weg dahin -> weiterlaufen lassen
        return this.safeTicks < SAFE_TICKS_BEFORE_EXIT;
    }

    @Override
    public void stop() {
        if (this.isHiding) {
            this.survivor.setHiddenInTent(false);
        }
        this.survivor.getNavigation().stop();
        this.isHiding = false;
        this.safeTicks = 0;
    }

    private BlockPos getTentAnchor() {
        SurvivorTentGoal tentGoal = this.survivor.getTentGoal();
        return tentGoal != null ? tentGoal.getTentAnchorPos() : null;
    }
}
