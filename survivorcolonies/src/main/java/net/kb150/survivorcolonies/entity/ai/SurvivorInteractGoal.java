package net.kb150.survivorcolonies.entity.ai;

import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

public class SurvivorInteractGoal extends Goal {
    private final SurvivorEntity survivor;

    public SurvivorInteractGoal(SurvivorEntity survivor) {
        this.survivor = survivor;
        // Blockiert Bewegung, Blickrichtung und Springen vollständig während des Gesprächs!
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        Player player = this.survivor.getTradingPlayer();
        if (player == null || !player.isAlive()) {
            return false;
        }
        // Bricht ab, wenn der Spieler wegläuft (> 8 Blöcke Distanz)
        return this.survivor.distanceToSqr(player) <= 64.0D;
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
            // Dreht den Kopf & Körper sanft zum Spieler
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