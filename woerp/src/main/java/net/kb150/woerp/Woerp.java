package net.kb150.woerp;

import com.mojang.logging.LogUtils;
import net.kb150.woerp.config.WoerpConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(Woerp.MODID)
public class Woerp {
    public static final String MODID = "woerp";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Woerp() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        
        // Registriert die serverconfig datei im world/serverconfig ordner
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, WoerpConfig.SERVER_SPEC);
        
        MinecraftForge.EVENT_BUS.register(this);
    }
}