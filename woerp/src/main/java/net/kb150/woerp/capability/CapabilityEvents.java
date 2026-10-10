package net.kb150.woerp.capability;

import net.kb150.woerp.Woerp;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public class CapabilityEvents {

    // MOD-Bus: Informiert Forge, dass diese Capability existiert
    @Mod.EventBusSubscriber(modid = Woerp.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void registerCaps(RegisterCapabilitiesEvent event) {
            event.register(IChunkRadiation.class);
        }
    }

    // FORGE-Bus: Klebt die Instanz an jeden echten LevelChunk
    @Mod.EventBusSubscriber(modid = Woerp.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeBusEvents {
        public static final ResourceLocation CHUNK_RAD_ID = new ResourceLocation(Woerp.MODID, "radiation");

        @SubscribeEvent
        public static void onAttachCapabilitiesChunk(AttachCapabilitiesEvent<LevelChunk> event) {
            if (!event.getObject().getCapability(ChunkRadiationProvider.RADIATION_CAP).isPresent()) {
                event.addCapability(CHUNK_RAD_ID, new ChunkRadiationProvider());
            }
        }
    }
}