package net.kb150.dragoncolonies;

import com.minecolonies.api.sounds.EventType;
import com.minecolonies.api.sounds.ModSoundEvents;
import net.kb150.dragoncolonies.data.WildDragonData;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.bookofdragons.entity.state.TransportMode;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.minecolonies.api.util.Tuple;
import net.kb150.dragoncolonies.network.DragonColoniesNetwork;
import net.kb150.dragoncolonies.registry.DragonColoniesRegistries;
import net.kb150.dragoncolonies.export.DragonExportManager;
import net.kb150.dragoncolonies.config.DragonColoniesConfig;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import com.minecolonies.api.creativetab.ModCreativeTabs;
import com.minecolonies.api.sounds.EventType;
import com.minecolonies.api.sounds.ModSoundEvents;
import com.minecolonies.api.util.Tuple;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraftforge.fml.ModLoadingContext;


import java.util.List;
import java.util.Map;

@Mod(DragonColonies.MOD_ID)
public class DragonColonies {

    public static final String MOD_ID = "dragoncolonies";
    public static final Logger LOGGER = LogManager.getLogger();

    public static boolean isLogging(String channel) {
        return DragonColoniesConfig.isLogging(channel);
    }

    public static void debug(String channel, String message, Object... args) {
        if (isLogging(channel)) {
            LOGGER.warn("[" + channel + "] " + message, args);
        }
    }

    public static void debugStack(String channel, String message) {
        if (isLogging(channel)) {
            LOGGER.warn("[" + channel + "] " + message, new Throwable(channel + "-Trace"));
        }
    }

    public DragonColonies() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, DragonColoniesConfig.SPEC);
        modEventBus.addListener(DragonColoniesConfig::onConfigLoad);

        DragonColoniesRegistries.register(modEventBus);
        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::addCreativeTabContents);

        MinecraftForge.EVENT_BUS.register(this);
    }

    // Injects Dragon Roost hut item directly into the Minecolonies 'mchuts' creative tab.
    private void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTab() == ModCreativeTabs.HUTS.get() 
                || event.getTabKey().location().equals(new net.minecraft.resources.ResourceLocation("minecolonies", "mchuts"))) {
            event.accept(DragonColoniesRegistries.ITEM_HUT_DRAGON_ROOST.get());
        }
    }

	@net.minecraftforge.eventbus.api.SubscribeEvent
    public void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END && event.getServer() != null) {
            net.kb150.dragoncolonies.ai.DragonNavigationHandler.serverTick(event.getServer());
        }
    }
	
    // Materializes escaped dragons onto the surface when their destination chunk finishes loading
    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() == null || event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!(event.getChunk() instanceof LevelChunk levelChunk)) return;

        WildDragonData data = WildDragonData.get(serverLevel);
        List<WildDragonData.PendingWildDragon> pending = data.popDragonsForChunk(levelChunk.getPos().toLong());
        if (pending.isEmpty()) return;

        for (WildDragonData.PendingWildDragon entry : pending) {
            int spawnX = entry.targetX != 0 ? entry.targetX : (levelChunk.getPos().getMinBlockX() + 8);
            int spawnZ = entry.targetZ != 0 ? entry.targetZ : (levelChunk.getPos().getMinBlockZ() + 8);
            int spawnY = serverLevel.getHeight(Heightmap.Types.WORLD_SURFACE, spawnX, spawnZ);

            Entity entity = EntityType.loadEntityRecursive(entry.dragonData, serverLevel, e -> {
                e.moveTo(spawnX + 0.5D, spawnY + 1.0D, spawnZ + 0.5D, serverLevel.random.nextFloat() * 360.0F, 0.0F);
                return e;
            });

            if (entity instanceof DragonBase wildDragon) {
                wildDragon.setTransportMode(TransportMode.GROUNDED);
                wildDragon.setCommand(0);
                serverLevel.addFreshEntity(wildDragon);

                serverLevel.sendParticles(ParticleTypes.CLOUD, spawnX + 0.5D, spawnY + 1.2D, spawnZ + 0.5D, 18, 1.0D, 0.5D, 1.0D, 0.02D);
                LOGGER.info("[WILD-EMERGE] Escaped dragon '{}' re-emerged into world at [{}, {}, {}] in chunk [{}, {}]",
                        wildDragon.getName().getString(), spawnX, spawnY, spawnZ, levelChunk.getPos().x, levelChunk.getPos().z);
            }
        }
    }

    private void setup(final FMLCommonSetupEvent event) {
        DragonColoniesNetwork.register(); 

        event.enqueueWork(() -> {
            DragonExportManager.loadConfig();
            Map<EventType, List<Tuple<SoundEvent, SoundEvent>>> unemployedSounds = ModSoundEvents.CITIZEN_SOUND_EVENTS.get("unemployed");

            if (unemployedSounds != null) {
                ModSoundEvents.CITIZEN_SOUND_EVENTS.put("beastmaster", unemployedSounds);
                ModSoundEvents.CITIZEN_SOUND_EVENTS.put("dragonrider", unemployedSounds);
                //LOGGER.info("[DragonColonies] Custom-Job-Sounds erfolgreich registriert!");
            } else {
                LOGGER.warn("[DragonColonies] Sound-Vorlage 'unemployed' konnte nicht in Minecolonies gefunden werden!");
            }
        });
    }
}