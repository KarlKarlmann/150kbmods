package net.kb150.survivorcolonies.entity.ai;

import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Höchste Bewegungs-Priorität im Goal-Selector (noch vor Avoid/Melee!): Sobald überhaupt
 * eine Fluchtreaktion fällig wäre (persönlichkeitsbasierte Schwelle, siehe
 * SurvivorEntity#wantsToFleeFromTarget), rennt der Survivor GEZIELT zum Zelt statt planlos
 * in die Wildnis. "Ist er erstmal irgendwo im Nirgendwo, hat er eh verloren" - AvoidEntityGoal
 * bleibt daher nur noch als Fallback aktiv, falls gerade kein Zelt existiert.
 *
 * Er läuft nur bis VOR den Eingang (normal per Wegfindung erreichbar - der 1-Block-hohe Gang
 * selbst ist für normale Pfadfindung unpassierbar), wird dort per Teleport reingesetzt und
 * unsichtbar/kollisionsfrei (siehe SurvivorEntity#setHiddenInTent). Ein hartes Sicherheitsnetz
 * (MAX_HIDDEN_TICKS) sorgt dafür, dass er NIE dauerhaft verschwunden bleiben kann, selbst wenn
 * aus irgendeinem Grund die reguläre "sicher genug"-Bedingung nie eintritt.
 */
public class SurvivorFleeToTentGoal extends Goal {
    private final SurvivorEntity survivor;

    private boolean isHiding = false;
    private int safeTicks = 0;
    private int hiddenSafetyTimer = 0;
    private int sleepParticleTimer = 0;
    private boolean tentWasDestroyed = false;

    private static final int SAFE_TICKS_BEFORE_EXIT = 60;  // ~3 Sekunden Ruhe, bevor er von selbst rauskommt
    private static final int MAX_HIDDEN_TICKS = 6000;       // ~5 Minuten harte Obergrenze, komme was wolle
    private static final int SLEEP_PARTICLE_INTERVAL = 60;  // alle ~3 Sekunden ein kleines Partikel-Zeichen

    public SurvivorFleeToTentGoal(SurvivorEntity survivor) {
        this.survivor = survivor;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (this.isHiding) return false; // läuft schon, siehe canContinueToUse()

        BlockPos approach = getTentApproach();
        return approach != null && isInDanger();
    }

    /** Nutzt dieselbe persönlichkeitsbasierte Schwelle wie der AvoidEntityGoal-Fallback. */
    private boolean isInDanger() {
        return this.survivor.wantsToFleeFromTarget();
    }

    @Override
    public void start() {
        this.isHiding = false;
        this.safeTicks = 0;
        this.hiddenSafetyTimer = 0;
        this.sleepParticleTimer = 0;
        this.tentWasDestroyed = false;
    }

    @Override
    public void tick() {
        BlockPos approach = getTentApproach();
        if (approach == null) return; // Zelt inzwischen weg -> canContinueToUse beendet das gleich

        if (!this.isHiding) {
            // Nur bis VOR den Eingang laufen - normal begehbar, kein Problem für die Wegfindung.
            this.survivor.getNavigation().moveTo(approach.getX() + 0.5D, approach.getY(), approach.getZ() + 0.5D, 1.1D);

            if (this.survivor.distanceToSqr(approach.getX() + 0.5D, approach.getY(), approach.getZ() + 0.5D) < 1.2D) {
                this.survivor.getNavigation().stop();

                BlockPos inside = getTentInside();
                BlockPos hideTarget = inside != null ? inside : approach; // Notfall-Fallback
                this.survivor.setPos(hideTarget.getX() + 0.5D, hideTarget.getY(), hideTarget.getZ() + 0.5D);
                this.survivor.setHiddenInTent(true);

                this.isHiding = true;
                this.safeTicks = 0;
                this.hiddenSafetyTimer = 0;
            }
        } else {
            this.hiddenSafetyTimer++;

            this.sleepParticleTimer++;
            if (this.sleepParticleTimer >= SLEEP_PARTICLE_INTERVAL) {
                this.sleepParticleTimer = 0;
                BlockPos inside = getTentInside();
                if (inside != null) {
                    this.survivor.spawnSleepParticles(inside);
                }
            }

            // Dach über ihm entfernt, während er sich versteckt? -> sofort raus damit.
            SurvivorTentGoal tentGoal = this.survivor.getTentGoal();
            if (tentGoal != null && !tentGoal.isTentIntact()) {
                this.tentWasDestroyed = true;
            }

            boolean stillInDanger = isInDanger();
            this.safeTicks = stillInDanger ? 0 : this.safeTicks + 1;
        }
    }

    @Override
    public boolean canContinueToUse() {
        BlockPos approach = getTentApproach();
        if (approach == null) return false; // Zelt weg -> raus damit

        if (!this.isHiding) return true; // noch auf dem Weg dahin -> weiterlaufen lassen

        // Aufwachen entweder weil's sicher genug ist, ODER spätestens beim harten Sicherheitsnetz,
        // ODER weil das Zelt zerstört wurde - er darf NIE dauerhaft unsichtbar/kollisionslos bleiben.
        return !this.tentWasDestroyed
                && this.safeTicks < SAFE_TICKS_BEFORE_EXIT
                && this.hiddenSafetyTimer < MAX_HIDDEN_TICKS;
    }

    @Override
    public void stop() {
        if (this.isHiding) {
            BlockPos approach = getTentApproach();
            if (approach != null) {
                // Erst zurück nach draußen teleportieren, dann sichtbar werden - nicht mitten
                // in den Wollblöcken auftauchen. WICHTIG: das muss VOR notifyDestroyed() passieren,
                // solange approach noch einen gültigen Wert hat.
                this.survivor.setPos(approach.getX() + 0.5D, approach.getY(), approach.getZ() + 0.5D);
            }
            this.survivor.setHiddenInTent(false);
        }

        if (this.tentWasDestroyed) {
            SurvivorTentGoal tentGoal = this.survivor.getTentGoal();
            if (tentGoal != null) {
                tentGoal.notifyDestroyed(); // SurvivorTentGoal war inaktiv (STANDBY) und weiß sonst nichts davon
            }
        }

        this.survivor.getNavigation().stop();
        this.isHiding = false;
        this.safeTicks = 0;
        this.hiddenSafetyTimer = 0;
        this.tentWasDestroyed = false;
    }

    private BlockPos getTentApproach() {
        SurvivorTentGoal tentGoal = this.survivor.getTentGoal();
        return tentGoal != null ? tentGoal.getTentApproachPos() : null;
    }

    private BlockPos getTentInside() {
        SurvivorTentGoal tentGoal = this.survivor.getTentGoal();
        return tentGoal != null ? tentGoal.getTentAnchorPos() : null;
    }
}
