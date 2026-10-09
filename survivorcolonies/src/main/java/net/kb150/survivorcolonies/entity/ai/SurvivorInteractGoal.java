package net.kb150.survivorcolonies.entity.ai;

import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

public class SurvivorInteractGoal extends Goal {
    private final SurvivorEntity survivor;
    
    // Verhindert ein Einfrieren des NPCs, wenn der Spieler das Menue schliesst, aber im Nahbereich stehen bleibt
    private static final double MAX_INTERACTION_DIST_SQ = 12.25D;

    public SurvivorInteractGoal(SurvivorEntity survivor) {
        this.survivor = survivor;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        Player player = this.survivor.getTradingPlayer();
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return false;
        }
        return this.survivor.distanceToSqr(player) <= MAX_INTERACTION_DIST_SQ;
    }

    @Override
    public void start() {
        this.survivor.getNavigation().stop();
    }

    @Override
    public void tick() {
        Player player = this.survivor.getTradingPlayer();
        if (player != null) {
            this.survivor.getNavigation().stop();
            this.survivor.getLookControl().setLookAt(player, 30.0F, 30.0F);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void stop() {
        this.survivor.setTradingPlayer(null);
    }
}