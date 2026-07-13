package net.kb150.everyonehashats;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(EveryoneHasHats.MODID)
public class EveryoneHasHats {
    public static final String MODID = "everyonehashats";
    public static final Logger LOGGER = LogManager.getLogger();

    public EveryoneHasHats() {
        // Registriere die Config-Schnittstelle
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, EveryoneHasHatsConfig.SPEC);
        LOGGER.info("[EveryoneHasHats] Bereit, das ultimative Hutendlager auf eurer Koppel zu errichten!");
    }
}