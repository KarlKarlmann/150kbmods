package net.kb150.dragoncolonies.blocks;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.tileentities.MinecoloniesTileEntities;
import net.kb150.dragoncolonies.registry.DragonColoniesRegistries;
import net.kb150.dragoncolonies.DragonColonies;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockHutDragonRoost extends AbstractBlockHut<BlockHutDragonRoost> {
    
    @Override
    public @NotNull String getHutName() {
        return "blockhutdragonroost";
    }

    // HIER IST DER FIX:
    @Override
    public ResourceLocation getRegistryName() {
        return new ResourceLocation(DragonColonies.MOD_ID, this.getHutName());
    }

    @Override
    public BuildingEntry getBuildingEntry() {
        return DragonColoniesRegistries.DRAGON_ROOST_BUILDING.get();
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