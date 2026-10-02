package net.kb150.superbcarfare;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("superbcarfare")
public class SuperbCarfare {

    public static final Logger LOGGER = LogManager.getLogger("SuperbCarfare");

    public SuperbCarfare() {
        LOGGER.info("SuperbCarfare (UCM Bridge) geladen!");
        // Alles andere passiert ueber Event-Subscriber (Mixin & FluidHandler)
    }
}