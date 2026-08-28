package net.kb150.survivorcolonies;

import com.mojang.logging.LogUtils;
import net.kb150.survivorcolonies.data.SurvivorDataLoader;
import net.kb150.survivorcolonies.entity.ModEntities;
import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.kb150.survivorcolonies.network.ModMessages;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(SurvivorColonies.MODID)
public class SurvivorColonies {
    public static final String MODID = "survivorcolonies";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SurvivorColonies() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 1. Entity DeferredRegister am Mod-Event-Bus anmelden
        ModEntities.register(modEventBus);

        // 2. Event-Listener für Lifecycle und Entity-Attribute
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerEntityAttributes);

        // 3. Forge Event-Bus für Runtime-Events (ReloadListener etc.)
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Netzwerk-Kanal und Pakete registrieren
            ModMessages.register();
            LOGGER.info("Survivor Colonies erfolgreich geladen.");
        });
    }

    private void registerEntityAttributes(EntityAttributeCreationEvent event) {
        // Verbindet die Attribute aus SurvivorEntity mit dem registrierten EntityType
        event.put(ModEntities.SURVIVOR.get(), SurvivorEntity.createAttributes().build());
    }

    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        // Registriert den JSON-DataLoader für Ausrüstung, Namen, Pools & Kosten
        event.addListener(new SurvivorDataLoader());
    }
}