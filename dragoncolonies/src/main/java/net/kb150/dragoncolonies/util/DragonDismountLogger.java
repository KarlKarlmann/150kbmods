package net.kb150.dragoncolonies.util;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.kb150.dragoncolonies.DragonColonies;
import net.kb150.dragoncolonies.ai.AbstractEntityAIDragonRider;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DragonColonies.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DragonDismountLogger {

    @SubscribeEvent
    public static void onEntityMount(EntityMountEvent event) {
        // Kanalfilter und Serverprüfung in einer Zeile
        if (event.getEntity().level().isClientSide() || !DragonColonies.isLogging("DISMOUNT")) return;

        if (event.isDismounting() 
                && event.getEntityBeingMounted() instanceof DragonBase dragon
                && event.getEntityMounting() instanceof AbstractEntityCitizen citizen) {
            
            AbstractEntityAIDragonRider<?, ?> rider = AbstractEntityAIDragonRider.getRiderForCitizen(citizen);

            CitizenReasonResolver.ReasonInfo reason = CitizenReasonResolver.resolveReason(citizen);

            DragonColonies.debug("DISMOUNT", "Bürger: {} ({}) | Drache: {} ({}) | Pos: {} | Reason: {} | Returning: {}",
                    citizen.getName().getString(), citizen.getUUID(),
                    dragon.getName().getString(), dragon.getUUID(),
                    citizen.position(),
                    reason,
                    rider != null && rider.isReturningDragon());
            DragonColonies.debugStack("DISMOUNT", "Dismount-Ursprung");
        }
    }
}