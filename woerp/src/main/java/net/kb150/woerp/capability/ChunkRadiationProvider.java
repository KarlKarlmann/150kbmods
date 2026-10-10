package net.kb150.woerp.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ChunkRadiationProvider implements ICapabilitySerializable<CompoundTag> {
    
    // Globale Referenz auf unsere Capability. Wird von Forge beim Laden injiziert.
    public static final Capability<IChunkRadiation> RADIATION_CAP = CapabilityManager.get(new CapabilityToken<>() {});

    private IChunkRadiation backend = null;
    private final LazyOptional<IChunkRadiation> optionalData = LazyOptional.of(this::createBackend);

    private @NotNull IChunkRadiation createBackend() {
        if (this.backend == null) {
            this.backend = new ChunkRadiation();
        }
        return this.backend;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == RADIATION_CAP) {
            return optionalData.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        if (this.createBackend() instanceof ChunkRadiation chunkRad) {
            return chunkRad.save();
        }
        return new CompoundTag();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (this.createBackend() instanceof ChunkRadiation chunkRad) {
            chunkRad.load(nbt);
        }
    }
}