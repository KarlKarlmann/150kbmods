package net.kb150.dragoncolonies;

import com.minecolonies.api.sounds.EventType;
import com.minecolonies.api.sounds.ModSoundEvents;
import com.minecolonies.api.util.Tuple;
import net.kb150.dragoncolonies.network.DragonColoniesNetwork;
import net.kb150.dragoncolonies.registry.DragonColoniesRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

@Mod(DragonColonies.MOD_ID)
public class DragonColonies {

    public static final String MOD_ID = "dragoncolonies";
    public static final Logger LOGGER = LogManager.getLogger();

    public DragonColonies() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        DragonColoniesRegistries.register(modEventBus);
        modEventBus.addListener(this::setup);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event) {
        DragonColoniesNetwork.register(); 

        event.enqueueWork(() -> {
            Map<EventType, List<Tuple<SoundEvent, SoundEvent>>> unemployedSounds = ModSoundEvents.CITIZEN_SOUND_EVENTS.get("unemployed");

            if (unemployedSounds != null) {
                ModSoundEvents.CITIZEN_SOUND_EVENTS.put("beastmaster", unemployedSounds);
                ModSoundEvents.CITIZEN_SOUND_EVENTS.put("firefighter", unemployedSounds);
                ModSoundEvents.CITIZEN_SOUND_EVENTS.put("dragonrider", unemployedSounds);
                LOGGER.info("[DragonColonies] Custom-Job-Sounds erfolgreich registriert!");
            } else {
                LOGGER.warn("[DragonColonies] Sound-Vorlage 'unemployed' konnte nicht in Minecolonies gefunden werden!");
            }
        });
    }
}