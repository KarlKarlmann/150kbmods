package net.kb150.reward_box;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.kb150.reward_box.init.RewardBoxRegistry;
import net.kb150.reward_box.init.RewardBoxCreativeTab;
import net.kb150.reward_box.util.RewardBoxConfigManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(RewardBox.MODID)
public class RewardBox {
    public static final String MODID = "reward_box";
    public static final Logger LOGGER = LogManager.getLogger(RewardBox.class);

    public RewardBox() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 1. Core-Komponenten (Block, Item, BlockEntity, Creative Tab) auf dem Mod-Bus registrieren
        RewardBoxRegistry.register(modEventBus);
        RewardBoxCreativeTab.register(modEventBus);

        // 2. Lifecycle Events
        modEventBus.addListener(this::commonSetup);

        // 3. Registriert diese Instanz auf dem allgemeinen Forge Event Bus (für Reload-Listener etc.)
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[RewardBox] Core Engine erfolgreich initialisiert.");
    }

    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        // Bindet den Daten-Manager für alle data/<namespace>/reward_boxes/*.json Dateien ein
        event.addListener(new RewardBoxConfigManager());
    }
}