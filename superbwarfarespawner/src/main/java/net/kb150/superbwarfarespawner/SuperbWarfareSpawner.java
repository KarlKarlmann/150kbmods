package net.kb150.superbwarfarespawner;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("superbwarfarespawner")
public class SuperbWarfareSpawner {

    public SuperbWarfareSpawner() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        
        /* 
         * WARUM: Hier teilen wir Forge mit, dass unsere Mod eine serverseitige Config besitzt. 
         */
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, WorldVehicleSpawner.SERVER_CONFIG);
        
        // Registriert unseren Dummy-Marker für das Spawning-System
        ModEntities.ENTITIES.register(bus);
    }
}