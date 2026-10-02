package net.kb150.survivorcolonies.entity.ai;

import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;

import java.util.EnumSet;

public class SurvivorFleeToTentGoal extends Goal {
    private final SurvivorEntity survivor;

    private boolean isHiding = false;
    private int safeTicks = 0;
    private int hiddenSafetyTimer = 0;
    private int sleepParticleTimer = 0;
    private boolean tentWasDestroyed = false;

    private static final int SAFE_TICKS_BEFORE_EXIT = 60;
    private static final int MAX_HIDDEN_TICKS = 6000;
    private static final int SLEEP_PARTICLE_INTERVAL = 60;

    public SurvivorFleeToTentGoal(SurvivorEntity survivor) {
        this.survivor = survivor;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (this.isHiding) return false;

        BlockPos approach = getTentApproach();
        if (approach == null) return false;

        SurvivorTentGoal tentGoal = this.survivor.getTentGoal();
        if (tentGoal != null && !tentGoal.isTentIntact()) {
            return false;
        }

        boolean danger = isInDanger();
        if (danger) {
            net.kb150.survivorcolonies.SurvivorColonies.LOGGER.info("[FLEE-GOAL] canUse == TRUE! Target: {}, Health: {}/{}", 
                survivor.getTarget(), survivor.getHealth(), survivor.getMaxHealth());
        }
        return danger;
    }

    /**
     * Prüft anhand der individuellen Lebensschwelle, ob sich der Survivor in Gefahr befindet.
     */
    private boolean isInDanger() {
        return this.survivor.wantsToFleeFromTarget();
    }

    @Override
    public void start() {
        net.kb150.survivorcolonies.SurvivorColonies.LOGGER.info("[FLEE-GOAL] START called!");
        this.isHiding = false;
        this.safeTicks = 0;
        this.hiddenSafetyTimer = 0;
        this.sleepParticleTimer = 0;
        this.tentWasDestroyed = false;
    }

    @Override
    public void tick() {
        BlockPos approach = getTentApproach();
        if (approach == null) return;

        if (!this.isHiding) {
            // Er navigiert AUSSCHLIESSLICH zum freien Platz vor dem Zelt
            this.survivor.getNavigation().moveTo(approach.getX() + 0.5D, approach.getY(), approach.getZ() + 0.5D, 1.2D);

            double dx = this.survivor.getX() - (approach.getX() + 0.5D);
            double dz = this.survivor.getZ() - (approach.getZ() + 0.5D);

            // Ankunft vor dem Zelt: Stoppen und direkt ins Innere teleportieren
            if (dx * dx + dz * dz < 1.5D) {
                this.survivor.getNavigation().stop();

                BlockPos inside = getTentInside();
                BlockPos hideTarget = inside != null ? inside : approach;
                this.survivor.setPos(hideTarget.getX() + 0.5D, hideTarget.getY(), hideTarget.getZ() + 0.5D);
                this.survivor.setHiddenInTent(true);

                this.isHiding = true;
                this.safeTicks = 0;
                this.hiddenSafetyTimer = 0;
            }
        } else {
            // Position im Zelt festhalten
            BlockPos inside = getTentInside();
            if (inside != null) {
                this.survivor.setPos(inside.getX() + 0.5D, inside.getY(), inside.getZ() + 0.5D);
            }

            this.hiddenSafetyTimer++;

            this.sleepParticleTimer++;
            if (this.sleepParticleTimer >= SLEEP_PARTICLE_INTERVAL) {
                this.sleepParticleTimer = 0;
                if (inside != null) {
                    this.survivor.spawnSleepParticles(inside);
                }
            }

            SurvivorTentGoal tentGoal = this.survivor.getTentGoal();
            if (tentGoal != null && !tentGoal.isTentIntact()) {
                this.tentWasDestroyed = true;
            }

            // Nicht rauskommen, solange Monster noch in der Nähe des Zeltes lauern!
            boolean monstersNearby = !this.survivor.level().getEntitiesOfClass(
                Monster.class, 
                this.survivor.getBoundingBox().inflate(10.0D)
            ).isEmpty();

            boolean stillInDanger = isInDanger() || monstersNearby;
            this.safeTicks = stillInDanger ? 0 : this.safeTicks + 1;
        }
    }

    @Override
    public boolean canContinueToUse() {
        BlockPos approach = getTentApproach();
        if (approach == null) return false;

        if (!this.isHiding) return true;

        boolean cont = !this.tentWasDestroyed
                && this.safeTicks < SAFE_TICKS_BEFORE_EXIT
                && this.hiddenSafetyTimer < MAX_HIDDEN_TICKS;

        if (!cont) {
            net.kb150.survivorcolonies.SurvivorColonies.LOGGER.info("[FLEE-GOAL] STOPPING! SafeTicks: {}, HiddenTimer: {}, Destroyed: {}", 
                safeTicks, hiddenSafetyTimer, tentWasDestroyed);
        }
        return cont;
    }

    @Override
    public void stop() {
        net.kb150.survivorcolonies.SurvivorColonies.LOGGER.info("[FLEE-GOAL] STOP called! WasHiding: {}", this.isHiding);
        if (this.isHiding) {
            BlockPos approach = getTentApproach();
            if (approach != null) {
                // Vor dem Aufdecken erst sicher nach draußen vor das Zelt teleportieren
                this.survivor.setPos(approach.getX() + 0.5D, approach.getY(), approach.getZ() + 0.5D);
            }
            this.survivor.setHiddenInTent(false);
        }

        if (this.tentWasDestroyed) {
            SurvivorTentGoal tentGoal = this.survivor.getTentGoal();
            if (tentGoal != null) {
                tentGoal.notifyDestroyed();
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
        if (tentGoal != null && tentGoal.getTentApproachPos() != null) {
            return tentGoal.getTentApproachPos();
        }
        return this.survivor.getKnownTentPos();
    }

    private BlockPos getTentInside() {
        SurvivorTentGoal tentGoal = this.survivor.getTentGoal();
        if (tentGoal != null && tentGoal.getTentAnchorPos() != null) {
            return tentGoal.getTentAnchorPos();
        }
        return this.survivor.getKnownTentPos();
    }
}