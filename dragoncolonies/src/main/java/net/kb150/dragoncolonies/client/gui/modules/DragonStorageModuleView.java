package net.kb150.dragoncolonies.client.gui.modules;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DragonStorageModuleView extends AbstractBuildingModuleView {

    private final List<CompoundTag> storedDragons = new ArrayList<>();
    private int capacity = 5;

    public List<CompoundTag> getStoredDragons() {
        return storedDragons;
    }

    public int getCapacity() {
        return capacity;
    }

    @Override
    public Component getDesc() {
        return Component.translatable("dragoncolonies.gui.module.dragon_storage.title");
    }

    @Override
    public ResourceLocation getIconResourceLocation() {
        return new ResourceLocation("dragoncolonies", "textures/gui/modules/roost.png");
    }

    @Override
    public BOWindow getWindow() {
        return new WindowDragonStorageModule(this);
    }

    @Override
    public void deserialize(@NotNull FriendlyByteBuf buf) {
        storedDragons.clear();
        CompoundTag syncTag = buf.readNbt();
        if (syncTag != null) {
            if (syncTag.contains("StoredDragons", Tag.TAG_LIST)) {
                ListTag dragonList = syncTag.getList("StoredDragons", Tag.TAG_COMPOUND);
                for (int i = 0; i < dragonList.size(); i++) {
                    storedDragons.add(dragonList.getCompound(i));
                }
            }
            if (syncTag.contains("Capacity")) {
                this.capacity = syncTag.getInt("Capacity");
            }
        }
    }
}