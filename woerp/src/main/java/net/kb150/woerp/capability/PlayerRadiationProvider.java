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

public class PlayerRadiationProvider implements ICapabilitySerializable<CompoundTag> {
    public static final Capability<IPlayerRadiation> PLAYER_RADIATION_CAP = CapabilityManager.get(new CapabilityToken<>() {});

    private IPlayerRadiation backend = null;
    private final LazyOptional<IPlayerRadiation> optionalData = LazyOptional.of(this::createBackend);

    private @NotNull IPlayerRadiation createBackend() {
        if (this.backend == null) this.backend = new PlayerRadiation();
        return this.backend;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == PLAYER_RADIATION_CAP ? optionalData.cast() : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return (createBackend() instanceof PlayerRadiation pr) ? pr.save() : new CompoundTag();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (createBackend() instanceof PlayerRadiation pr) pr.load(nbt);
    }
}