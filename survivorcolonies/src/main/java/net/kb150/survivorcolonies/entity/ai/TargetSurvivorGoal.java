package net.kb150.survivorcolonies.entity.ai;

import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;

public class TargetSurvivorGoal extends NearestAttackableTargetGoal<SurvivorEntity> {
    public TargetSurvivorGoal(Mob mob, boolean mustSee) {
        super(mob, SurvivorEntity.class, mustSee);
    }
}