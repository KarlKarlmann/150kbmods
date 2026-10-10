package net.kb150.woerp.block;

import net.kb150.woerp.Woerp;
import net.kb150.woerp.radiation.EmitterSavedData;
import net.kb150.woerp.radiation.RadiationEmitter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class WoerpStoneBlock extends Block {
    public WoerpStoneBlock(Properties properties) { super(properties); }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (level instanceof ServerLevel serverLevel) {
            long currentTick = serverLevel.getServer().getTickCount();
            
            // Intensität: 50.0, Radius: 32 Blöcke
            EmitterSavedData.get(serverLevel).addEmitter(new RadiationEmitter(pos, 50.0f, 32.0f, currentTick));
            Woerp.LOGGER.debug("[WÖRPER] Strahlungsemitter bei {} registriert.", pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            long currentTick = serverLevel.getServer().getTickCount();
            EmitterSavedData data = EmitterSavedData.get(serverLevel);

            // Abbau setzt endTick -> leitet die Zerfallsphase für umliegende Chunks ein
            for (RadiationEmitter emitter : data.getEmitters()) {
                if (emitter.pos.equals(pos) && emitter.isActive()) {
                    emitter.endTick = currentTick;
                    data.setDirty();
                    Woerp.LOGGER.debug("[WÖRPER] Emitter bei {} deaktiviert (Zerfall startet).", pos);
                    break;
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}