package net.kb150.woerp;

import com.mojang.logging.LogUtils;
import net.kb150.woerp.config.WoerpConfig;
import net.kb150.woerp.init.ModBlocks;
import net.kb150.woerp.init.ModEffects;
import net.kb150.woerp.network.WoerpNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(Woerp.MODID)
public class Woerp {
    public static final String MODID = "woerp";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Woerp() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, WoerpConfig.SERVER_SPEC);
        
        ModBlocks.register(modBus);
        ModEffects.register(modBus);
        
        modBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);

        // Soft-Dependency verhindert NoClassDefFoundError auf Dedicated Servern und Setups ohne Epic Fight
        if (FMLEnvironment.dist.isClient() && ModList.get().isLoaded("epicfight")) {
            modBus.register(net.kb150.woerp.client.compat.epicfight.EpicFightCompat.class);
            LOGGER.info("[WOERP] Epic Fight Bridge erfolgreich am Mod-Bus registriert.");
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            WoerpNetwork.register();
            LOGGER.info("[WOERP] Netzwerk-Kanal erfolgreich initialisiert.");
        });
    }
}