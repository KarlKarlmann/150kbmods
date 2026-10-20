package net.kb150.woerp.event;

import net.kb150.woerp.Woerp;
import net.kb150.woerp.block.WoerpStoneBlock;
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

        BlockState state = event.getState();
        float emission = 0.0f;

        if (state.getBlock() instanceof WoerpStoneBlock) {
            // 10 Stufen teilen die 500er-Decke in 50er-Schritte auf; State-Palette spart NBT-Sync
            emission = state.getValue(WoerpStoneBlock.PURITY) * 50.0f;
        } else {
            TagEmitterLevel tagLevel = TagEmitterLevel.getForState(state);
            if (tagLevel != null) emission = tagLevel.emission;
        }

        // Info-Log entlarvt fehlschlagende Tag-Zuordnungen auf Dedicated Servern sofort
        Woerp.LOGGER.info("[WOERP-PLACE] Block: {} | Emission: {}", state.getBlock().getDescriptionId(), emission);

        if (emission > 0.0f) {
            EmitterSavedData.get(level).addEmitter(new RadiationEmitter(event.getPos(), emission, level.getServer().getTickCount()));
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockState state = event.getState();
        boolean isEmitter = state.getBlock() instanceof WoerpStoneBlock || TagEmitterLevel.getForState(state) != null;

        if (isEmitter) {
            long tick = level.getServer().getTickCount();
            BlockPos pos = event.getPos();
            EmitterSavedData data = EmitterSavedData.get(level);

            for (RadiationEmitter emitter : data.getEmitters()) {
                if (emitter.pos.equals(pos) && emitter.isActive()) {
                    emitter.endTick = tick;
                    data.setDirty();
                    // Info-Level stellt sicher, dass das Loeschen im Server-Log nachvollziehbar bleibt
                    Woerp.LOGGER.info("[WOERP-BREAK] Emitter bei {} abgebaut. Zerfall aktiv.", pos);
                    break;
                }
            }
        }
    }
}