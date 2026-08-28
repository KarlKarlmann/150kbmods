package net.kb150.dragoncolonies.util;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "dragoncolonies", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DragonDismountLogger {

    @SubscribeEvent
    public static void onEntityMount(EntityMountEvent event) {
        // NUR AUF DEM SERVER LOGGEN
        if (event.getEntity().level().isClientSide()) return;

        if (event.isDismounting() 
                && event.getEntityBeingMounted() instanceof DragonBase dragon
                && event.getEntityMounting() instanceof AbstractEntityCitizen citizen) {
            
            System.err.println("==================================================");
            System.err.println("[DRAGON-DEBUG-SERVER] SERVER-ABWURF ERKANNT!");
            System.err.println("Bürger: " + citizen.getName().getString());
            System.err.println("Drache: " + dragon.getName().getString());
            System.err.println("Auslösender Server-Stacktrace:");
            
            new Exception("[DRAGON-DEBUG-SERVER] Dismount Stacktrace").printStackTrace();
            
            System.err.println("==================================================");
        }
    }
}