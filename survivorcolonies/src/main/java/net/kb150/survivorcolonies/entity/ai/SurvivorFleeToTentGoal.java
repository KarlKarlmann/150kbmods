package net.kb150.survivorcolonies.entity.ai;

import net.kb150.survivorcolonies.SurvivorColonies;
import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;

import java.util.EnumSet;

public class SurvivorFleeToTentGoal extends Goal {
    private final SurvivorEntity survivor;
    private State state = State.FLEEING;

    private int safeTicks = 0;
    private int hiddenSafetyTimer = 0;
    private int sleepParticleTimer = 0;
    private int navigateTimer = 0;
    private boolean tentWasDestroyed = false;

    private static final int SAFE_TICKS_BEFORE_EXIT = 60;
    private static final int MAX_HIDDEN_TICKS = 6000;
    private static final int SLEEP_PARTICLE_INTERVAL = 60;
    private static final int MAX_NAVIGATE_TICKS = 200;

    private enum State { FLEEING, SLEEPING }

    public SurvivorFleeToTentGoal(SurvivorEntity survivor) {
        this.survivor = survivor;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    private BlockPos getTentApproach() { 
        return survivor.getKnownTentPos(); 
    }

    private BlockPos getTentInside() {
        BlockPos approach = getTentApproach();
        SurvivorTentGoal tentGoal = survivor.getTentGoal();
        return (approach != null && tentGoal != null) ? tentGoal.getTentInside(approach) : null;
    }

    @Override
    public boolean canUse() {
        if (this.state == State.SLEEPING) return false;

        BlockPos approach = getTentApproach();
        SurvivorTentGoal tentGoal = this.survivor.getTentGoal();
        
        if (approach == null || (tentGoal != null && !tentGoal.isTentIntact())) return false;

        return this.survivor.wantsToFleeFromTarget();
    }

    @Override
    public void start() {
        BlockPos inside = getTentInside();
        
        if (inside != null && isAtPos(inside)) {
            enterTent(inside, inside);
        } else {
            this.state = State.FLEEING;
            this.safeTicks = 0;
            this.hiddenSafetyTimer = 0;
            this.sleepParticleTimer = 0;
            this.navigateTimer = 0;
            this.tentWasDestroyed = false;
        }

        SurvivorColonies.debug("FLEE", "%s START -> State: %s (HP: %.1f/%.1f)", survivor.getSurvivorName(), this.state, survivor.getHealth(), survivor.getMaxHealth());
    }

    @Override
    public void tick() {
        BlockPos approach = getTentApproach();
        BlockPos inside = getTentInside();
        if (approach == null) return;

        switch (this.state) {
            case FLEEING -> {
                this.navigateTimer++;
                
                boolean reachedApproach = survivor.distanceToSqr(approach.getX() + 0.5D, approach.getY(), approach.getZ() + 0.5D) < 2.25D 
                                          || survivor.getNavigation().isDone() 
                                          || this.navigateTimer >= MAX_NAVIGATE_TICKS;

                if ((inside != null && isAtPos(inside)) || reachedApproach) {
                    enterTent(approach, inside);
                    SurvivorColonies.debug("FLEE", "%s wechselt FLEE -> SLEEPING (Pos: %s)", survivor.getSurvivorName(), survivor.blockPosition());
                } else {
                    survivor.getNavigation().moveTo(approach.getX() + 0.5D, approach.getY(), approach.getZ() + 0.5D, 1.2D);
                }
            }
            case SLEEPING -> {
                if (inside != null) survivor.setPos(inside.getX() + 0.5D, inside.getY(), inside.getZ() + 0.5D);

                if (survivor.tickCount % 20 == 0 && survivor.getHealth() < survivor.getMaxHealth()) survivor.heal(1.0F);

                this.hiddenSafetyTimer++;
                this.sleepParticleTimer++;
                
                if (this.sleepParticleTimer >= SLEEP_PARTICLE_INTERVAL) {
                    this.sleepParticleTimer = 0;
                    if (inside != null) survivor.spawnSleepParticles(inside);
                }

                SurvivorTentGoal tentGoal = survivor.getTentGoal();
                if (tentGoal != null && !tentGoal.isTentIntact()) this.tentWasDestroyed = true;

                boolean monstersNearby = !survivor.level().getEntitiesOfClass(Monster.class, survivor.getBoundingBox().inflate(10.0D)).isEmpty();
                this.safeTicks = (survivor.wantsToFleeFromTarget() || monstersNearby) ? 0 : this.safeTicks + 1;
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (getTentApproach() == null) return false;
        if (this.state == State.FLEEING) return this.navigateTimer < MAX_NAVIGATE_TICKS;

        if (!survivor.level().isNight() && survivor.getHealth() >= survivor.getMaxHealth()) {
            SurvivorColonies.debug("FLEE", "%s verlässt SLEEPING (Tag & Volle HP)", survivor.getSurvivorName());
            return false;
        }

        return !this.tentWasDestroyed && this.safeTicks < SAFE_TICKS_BEFORE_EXIT && this.hiddenSafetyTimer < MAX_HIDDEN_TICKS;
    }

    @Override
    public void stop() {
        SurvivorColonies.debug("FLEE", "%s STOP -> Beende State %s", survivor.getSurvivorName(), this.state);
        exitTent();

        SurvivorTentGoal tentGoal = survivor.getTentGoal();
        if (this.tentWasDestroyed && tentGoal != null) tentGoal.notifyDestroyed();

        this.state = State.FLEEING;
        this.safeTicks = 0;
        this.hiddenSafetyTimer = 0;
        this.navigateTimer = 0;
        this.tentWasDestroyed = false;
    }

    private void enterTent(BlockPos approach, BlockPos inside) {
        survivor.getNavigation().stop();
        BlockPos target = inside != null ? inside : approach;
        if (target == null) return;

        survivor.setPos(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D);
        survivor.setHiddenInTent(true);
        
        this.state = State.SLEEPING;
        this.safeTicks = 0;
        this.hiddenSafetyTimer = 0;
    }

    private void exitTent() {
        if (this.state != State.SLEEPING && !survivor.isInvisible()) return;

        BlockPos approach = getTentApproach();
        if (approach == null) return;

        survivor.getNavigation().stop();
        survivor.setPos(approach.getX() + 0.5D, approach.getY(), approach.getZ() + 0.5D);
        survivor.setHiddenInTent(false);
    }

    private boolean isAtPos(BlockPos target) {
        return survivor.blockPosition().distSqr(target) <= 2;
    }

    @Override
    public boolean isInterruptable() {
        return this.state != State.SLEEPING || survivor.getHealth() >= survivor.getMaxHealth();
    }
}