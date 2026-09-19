
package net.kb150.survivorcolonies.entity.ai;

import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class SurvivorSeekShelterGoal extends Goal {
    private final SurvivorEntity survivor;
    private BlockPos shelterPos;

    public SurvivorSeekShelterGoal(SurvivorEntity survivor) {
        this.survivor = survivor;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.survivor.isPassenger() || this.survivor.getTarget() != null || this.survivor.getTradingPlayer() != null) {
            return false;
        }

        // Nachts übernimmt ausschließlich das Zelt (SurvivorTentGoal) die Unterschlupf-Funktion.
        // Dieses Goal ist nur noch für Regen am Tag zuständig (z.B. unter einem Vordach warten).
        boolean badWeather = this.survivor.level().isRaining() && this.survivor.level().isDay();
        if (!badWeather) return false;

        BlockPos current = this.survivor.blockPosition();
        // Wenn er bereits unter einem Dach steht: Kein Handlungsbedarf!
        if (!this.survivor.level().canSeeSky(current)) return false;

        // Sucht Blöcke im Umkreis von 10 Blöcken, die den Himmel verdecken
        for (BlockPos pos : BlockPos.betweenClosed(current.offset(-10, -2, -10), current.offset(10, 3, 10))) {
            if (!this.survivor.level().canSeeSky(pos) 
                    && this.survivor.level().getBlockState(pos).isAir() 
                    && this.survivor.level().getBlockState(pos.below()).isSolid()) {
                this.shelterPos = pos.immutable();
                return true;
            }
        }
        return false;
    }

    @Override
    public void start() {
        if (this.shelterPos != null) {
            this.survivor.getNavigation().moveTo(
                this.shelterPos.getX() + 0.5D, 
                this.shelterPos.getY(), 
                this.shelterPos.getZ() + 0.5D, 
                1.0D
            );
        }
    }

    @Override
    public boolean canContinueToUse() {
        return this.shelterPos != null 
            && !this.survivor.getNavigation().isDone() 
            && (this.survivor.level().isRaining() || this.survivor.level().isNight())
            && this.survivor.getTarget() == null;
    }

    @Override
    public void stop() {
        this.shelterPos = null;
    }
}

