package net.kb150.dragoncolonies.buildings.modules;

import com.minecolonies.api.colony.IColony;
import net.kb150.dragoncolonies.buildings.BuildingFireStation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class FirefighterDispatchModule {

    private final BuildingFireStation fireStation;
    private int tickCounter = 0;

    public FirefighterDispatchModule(BuildingFireStation fireStation) {
        this.fireStation = fireStation;
    }

    public void tick() {
        tickCounter++;
        if (tickCounter >= 100) {
            tickCounter = 0;
            performFireScan();
        }
    }

    private void performFireScan() {
        IColony colony = fireStation.getColony();
        Level level = colony != null ? colony.getWorld() : null;
        
        if (level == null || level.isClientSide) {
            return; // Nur auf dem Server scannen!
        }

        // TODO: Den Radius an das Gebäude-Level anpassen (fireStation.getBuildingLevel())
        int scanRadius = 30 + (fireStation.getBuildingLevel() * 10);
        BlockPos center = fireStation.getPosition();

        // Hier würde später ein asynchroner oder über Ticks verteilter Scan laufen.
        // Zur Vereinfachung ist hier der konzeptionelle Ansatz:
        /*
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-scanRadius, -10, -scanRadius), center.offset(scanRadius, 20, scanRadius))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
                // FEUER ENTDECKT!
                dispatchFirefightersTo(pos);
                break; // Erstes Feuer melden reicht für diesen Scan
            }
        }
        */
	}
}