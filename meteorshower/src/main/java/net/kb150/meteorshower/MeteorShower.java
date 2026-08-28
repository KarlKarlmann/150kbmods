package net.kb150.meteorshower;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(MeteorShower.MOD_ID)
public class MeteorShower {
    public static final String MOD_ID = "meteorshower";
    public static final Logger LOGGER = LogManager.getLogger();

    public MeteorShower() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 1. Netzwerk-Setup auf dem MOD Event-Bus registrieren
        modEventBus.addListener(NetworkHandler::register);

        // 2. Server-Manager auf dem FORGE Event-Bus registrieren
        MinecraftForge.EVENT_BUS.register(MeteorServerManager.class);
    }
}