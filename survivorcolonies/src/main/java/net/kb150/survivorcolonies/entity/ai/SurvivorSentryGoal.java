package net.kb150.survivorcolonies.entity.ai;

import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class SurvivorSentryGoal extends Goal {
    private final SurvivorEntity survivor;
    private int lookTimer = 0;

    public SurvivorSentryGoal(SurvivorEntity survivor) {
        this.survivor = survivor;
        // Blockiert Bewegung vollständig – er bleibt einfach stehen!
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // Greift immer als Fallback, wenn keine höhere Priorität (Essen, Kampf, Lagerfeuer) aktiv ist
        return true;
    }

    @Override
    public void start() {
        this.survivor.getNavigation().stop();
    }

    @Override
    public void tick() {
        this.lookTimer++;
        // Alle 3 bis 6 Sekunden sichert er einen neuen Winkel im Umkreis
        if (this.lookTimer >= 60 + this.survivor.getRandom().nextInt(60)) {
            this.lookTimer = 0;
            BlockPos current = this.survivor.blockPosition();
            
            int offsetX = this.survivor.getRandom().nextInt(13) - 6;
            int offsetZ = this.survivor.getRandom().nextInt(13) - 6;
            
            this.survivor.getLookControl().setLookAt(
                current.getX() + offsetX, 
                current.getY() + 1.5, 
                current.getZ() + offsetZ
            );
        }
    }
}