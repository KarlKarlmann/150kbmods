package net.kb150.woerp.event;

import net.kb150.woerp.Woerp;
import net.kb150.woerp.radiation.EmitterSavedData;
import net.kb150.woerp.radiation.RadiationEmitter;
import net.kb150.woerp.radiation.TagEmitterLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Woerp.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TagRadiationHandler {

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        
        TagEmitterLevel tagLevel = TagEmitterLevel.getForState(event.getState());
        if (tagLevel != null) {
            long tick = level.getServer().getTickCount();
            BlockPos pos = event.getPos();
            
            EmitterSavedData.get(level).addEmitter(new RadiationEmitter(pos, tagLevel.emission, tagLevel.radius, tick));
            Woerp.LOGGER.debug("[WÖRPER] Tagged-Block ({}) bei {} als Emitter registriert.", tagLevel.name(), pos);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        
        TagEmitterLevel tagLevel = TagEmitterLevel.getForState(event.getState());
        if (tagLevel != null) {
            long tick = level.getServer().getTickCount();
            BlockPos pos = event.getPos();
            EmitterSavedData data = EmitterSavedData.get(level);

            // Abbau setzt endTick für sanften Fallout-Zerfall in den Nachbarchunks
            for (RadiationEmitter emitter : data.getEmitters()) {
                if (emitter.pos.equals(pos) && emitter.isActive()) {
                    emitter.endTick = tick;
                    data.setDirty();
                    Woerp.LOGGER.debug("[WÖRPER] Tagged-Emitter bei {} entfernt. Zerfall gestartet.", pos);
                    break;
                }
            }
        }
    }
}