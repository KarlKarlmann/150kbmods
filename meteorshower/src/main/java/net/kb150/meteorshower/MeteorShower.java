package net.kb150.meteorshower;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(MeteorShower.MOD_ID)
public class MeteorShower {
    public static final String MOD_ID = "meteorshower";
    public static final Logger LOGGER = LogManager.getLogger();

    public MeteorShower() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Legt die Config weltgebunden unter saves/<world>/serverconfig/meteorshower-server.toml ab
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, MeteorConfig.SERVER_SPEC);

        modEventBus.addListener(NetworkHandler::register);
        MinecraftForge.EVENT_BUS.register(MeteorServerManager.class);
    }
}