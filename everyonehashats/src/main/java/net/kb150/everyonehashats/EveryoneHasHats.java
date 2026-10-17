package net.kb150.everyonehashats;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(EveryoneHasHats.MODID)
public class EveryoneHasHats {
    public static final String MODID = "everyonehashats";
    public static final Logger LOGGER = LogManager.getLogger();

    public EveryoneHasHats() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, EveryoneHasHatsConfig.SPEC);
        
        // Registriert die Compat-Klasse NUR auf dem Client und NUR wenn Epic Fight geladen ist
        if (FMLEnvironment.dist.isClient() && ModList.get().isLoaded("epicfight")) {
            FMLJavaModLoadingContext.get().getModEventBus().register(net.kb150.everyonehashats.compat.epicfight.EpicFightCompat.class);
            LOGGER.info("[EveryoneHasHats] Epic Fight Bridge erfolgreich aktiviert!");
        }
        
        LOGGER.info("[EveryoneHasHats] Bereit, das ultimative Hutendlager auf eurer Koppel zu errichten!");
    }
}