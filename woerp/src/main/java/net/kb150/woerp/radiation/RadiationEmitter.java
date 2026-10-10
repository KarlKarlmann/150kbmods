package net.kb150.woerp.radiation;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public class RadiationEmitter {
    public final BlockPos pos;
    public final float baseEmission;
    public final float maxRadius;
    public final long startTick;
    public long endTick; // -1 wenn noch aktiv

    public RadiationEmitter(BlockPos pos, float baseEmission, float maxRadius, long startTick) {
        this.pos = pos;
        this.baseEmission = baseEmission;
        this.maxRadius = maxRadius;
        this.startTick = startTick;
        this.endTick = -1L;
    }

    // Für das Laden aus der NBT-Datenbank
    public RadiationEmitter(CompoundTag tag) {
        this.pos = BlockPos.of(tag.getLong("pos"));
        this.baseEmission = tag.getFloat("emission");
        this.maxRadius = tag.getFloat("radius");
        this.startTick = tag.getLong("start");
        this.endTick = tag.getLong("end");
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("pos", pos.asLong());
        tag.putFloat("emission", baseEmission);
        tag.putFloat("radius", maxRadius);
        tag.putLong("start", startTick);
        tag.putLong("end", endTick);
        return tag;
    }

    // Berechnet den Tick, ab dem dieser Emitter mathematisch "tot" ist
    public long getPurgeTick(float halfLifeTicks, float epsilon) {
        if (endTick == -1L) return Long.MAX_VALUE;

        // baseEmission * (0.5 ^ (ticks / halfLife)) < epsilon
        double ticksToDecay = halfLifeTicks * (Math.log(epsilon / baseEmission) / Math.log(0.5));
        return endTick + (long) Math.max(0, ticksToDecay);
    }
    
    public boolean isActive() {
        return endTick == -1L;
    }
}