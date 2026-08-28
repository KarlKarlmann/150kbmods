package net.kb150.dragoncolonies.blocks;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.tileentities.MinecoloniesTileEntities;
import net.kb150.dragoncolonies.registry.DragonColoniesRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.kb150.dragoncolonies.DragonColonies;

public class BlockHutFireStation extends AbstractBlockHut<BlockHutFireStation> {
    
    @Override
    public @NotNull String getHutName() {
        // Der Name, der intern für den Block-Typ verwendet wird.
        return "blockhutfirestation";
    }

    @Override
    public BuildingEntry getBuildingEntry() {
        // Verbindet diesen Blockknoten mit unserer Gebäude-Logik in der Registry
        return DragonColoniesRegistries.FIRE_STATION_BUILDING.get();
    }
	
	@Override
    public ResourceLocation getRegistryName() {
        return new ResourceLocation(DragonColonies.MOD_ID, this.getHutName());
    }
	
	@Nullable
	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
		// 1. Das TileEntity ganz normal erstellen
		BlockEntity entity = MinecoloniesTileEntities.BUILDING.get().create(pos, state);
		
		// 2. DEN FEHLER BEHEBEN: Die Registry-ID manuell in das TileEntity schreiben!
		if (entity instanceof com.minecolonies.core.tileentities.TileEntityColonyBuilding building) {
			building.registryName = this.getBuildingEntry().getRegistryName();
		}
		
		return entity;
	}
}