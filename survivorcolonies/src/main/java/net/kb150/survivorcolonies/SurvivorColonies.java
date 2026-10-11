package net.kb150.survivorcolonies;

import com.mojang.logging.LogUtils;
import net.kb150.survivorcolonies.config.SurvivorConfig;
import net.kb150.survivorcolonies.data.DialogManager;
import net.kb150.survivorcolonies.data.SurvivorDataLoader;
import net.kb150.survivorcolonies.entity.ModEntities;
import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.kb150.survivorcolonies.network.ModMessages;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(SurvivorColonies.MODID)
public class SurvivorColonies {
    public static final String MODID = "survivorcolonies";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static boolean isLogging(String channel) {
        return SurvivorConfig.isLogging(channel);
    }

	public static void debug(String channel, String message, Object... args) {
		if (isLogging(channel)) {
			// String.format löst %s Platzhalter korrekt auf, bevor Log4j die Zeile schreibt
			LOGGER.warn("[" + channel + "] " + String.format(message, args));
		}
	}

    public static void debugStack(String channel, String message) {
        if (isLogging(channel)) {
            LOGGER.warn("[" + channel + "] " + message, new Throwable(channel + "-Trace"));
        }
    }

    public SurvivorColonies() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModEntities.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerEntityAttributes);
        modEventBus.addListener(this::registerSpawnPlacements);

        // Config-Sync
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SurvivorConfig.SPEC);
        modEventBus.addListener(SurvivorConfig::onConfigLoad);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModMessages.register();
            LOGGER.info("Survivor Colonies erfolgreich geladen.");
        });
    }

    private void registerEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.SURVIVOR.get(), SurvivorEntity.createAttributes().build());
    }

    private void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(
            ModEntities.SURVIVOR.get(),
            SpawnPlacements.Type.ON_GROUND,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            SurvivorEntity::checkSurvivorSpawnRules,
            SpawnPlacementRegisterEvent.Operation.OR
        );
    }

    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SurvivorDataLoader());
        event.addListener(new DialogManager()); 
    }
}