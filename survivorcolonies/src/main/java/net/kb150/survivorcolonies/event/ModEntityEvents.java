package net.kb150.survivorcolonies.event;

import net.kb150.survivorcolonies.SurvivorColonies;
import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.kb150.survivorcolonies.entity.ai.TargetSurvivorGoal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SurvivorColonies.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModEntityEvents {

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // KI-Anpassungen dürfen nur serverseitig stattfinden
        if (event.getLevel().isClientSide()) {
            return;
        }

        if (event.getEntity() instanceof Mob mob) {
            // Reagiert auf alle Monster (Vanilla, MineColonies-Raider & Mod-Monster)
            if (mob instanceof Monster && !(mob instanceof Creeper)) {
                
                // Verhindert doppeltes Hinzufügen beim Wiederbetreten geladener Chunks
                boolean alreadyHasGoal = mob.targetSelector.getAvailableGoals().stream()
                        .anyMatch(wrappedGoal -> wrappedGoal.getGoal() instanceof TargetSurvivorGoal);

                if (!alreadyHasGoal) {
                    // Priorität 2 sorgt dafür, dass sie Überlebende aktiv ins Visier nehmen
                    mob.targetSelector.addGoal(2, new TargetSurvivorGoal(mob, true));
                }
            }
        }
    }
}