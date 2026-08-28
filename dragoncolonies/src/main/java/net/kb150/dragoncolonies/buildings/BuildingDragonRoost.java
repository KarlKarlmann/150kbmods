package net.kb150.dragoncolonies.buildings;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;
import net.kb150.dragoncolonies.buildings.modules.DragonStorageModule;
import net.kb150.dragoncolonies.registry.DragonColoniesRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class BuildingDragonRoost extends AbstractBuildingGuards {

    private static final String SCHEMATIC_NAME = "dragonroost";
    private static final int MAX_LEVEL = 5;

    public BuildingDragonRoost(@NotNull IColony colony, @NotNull BlockPos pos) {
        super(colony, pos);
    }

    public DragonStorageModule getStorageModule() {
        return this.getModule(DragonColoniesRegistries.DRAGON_STORAGE);
    }

    @Override
    public @NotNull String getSchematicName() {
        return SCHEMATIC_NAME;
    }

    @Override
    public int getMaxBuildingLevel() {
        return MAX_LEVEL;
    }

    @Override
    public void onDestroyed() {
        super.onDestroyed();

        DragonStorageModule storageModule = getStorageModule();
        if (storageModule != null && this.colony != null && this.colony.getWorld() != null && !this.colony.getWorld().isClientSide) {
            Level level = this.colony.getWorld();
            List<CompoundTag> savedDragons = storageModule.getAllDragons();

            for (CompoundTag dragonTag : savedDragons) {
                Entity entity = EntityType.loadEntityRecursive(dragonTag, level, (e) -> {
                    BlockPos spawnPos = this.getPosition();
                    e.moveTo(spawnPos.getX() + 0.5, spawnPos.getY() + 1.0, spawnPos.getZ() + 0.5, 0, 0);
                    return e;
                });

                if (entity != null) {
                    level.addFreshEntity(entity);
                }
            }
            
            storageModule.getStoredDragons().clear();
            storageModule.markDirty();
        }
    }

    public static class View extends AbstractBuildingGuards.View {

        public View(IColonyView colony, @NotNull BlockPos pos) {
            super(colony, pos);
        }

        @Override
        public @NotNull com.ldtteam.blockui.views.BOWindow getWindow() {
            return new net.kb150.dragoncolonies.client.gui.WindowDragonRoost(this);
        }
    }
}