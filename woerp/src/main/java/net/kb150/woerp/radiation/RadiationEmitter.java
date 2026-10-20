package net.kb150.woerp.radiation;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public class RadiationEmitter {
    // 384m deckt die gesamte Minecraft-Welthöhe ab (Y -64 bis 320)[cite: 3]
    public static final float GLOBAL_RADIUS = 384.0f; //[cite: 3]

    public final BlockPos pos; //[cite: 3]
    // Feld bleibt public fuer direkten Zugriff existierender Befehle, verliert aber final fuer dynamische Container-Skalierung[cite: 3]
    public float baseEmission; //[cite: 3]
    public final long startTick; //[cite: 3]
    public long endTick; //[cite: 3]

    public RadiationEmitter(BlockPos pos, float baseEmission, long startTick) {
        this.pos = pos; //[cite: 3]
        this.baseEmission = baseEmission; //[cite: 3]
        this.startTick = startTick; //[cite: 3]
        this.endTick = -1L; //[cite: 3]
    }

    public RadiationEmitter(CompoundTag tag) {
        this.pos = BlockPos.of(tag.getLong("pos")); //[cite: 3]
        this.baseEmission = tag.getFloat("emission"); //[cite: 3]
        this.startTick = tag.getLong("start"); //[cite: 3]
        this.endTick = tag.getLong("end"); //[cite: 3]
    }

    public float getBaseEmission() { return baseEmission; }
    
    // Ermoeglicht Inventar-Aenderungen ohne alte Emitter-Instanzen im World-Save zu fragmentieren
    public void setBaseEmission(float emission) { this.baseEmission = emission; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag(); //[cite: 3]
        tag.putLong("pos", pos.asLong()); //[cite: 3]
        tag.putFloat("emission", baseEmission); //[cite: 3]
        tag.putLong("start", startTick); //[cite: 3]
        tag.putLong("end", endTick); //[cite: 3]
        return tag; //[cite: 3]
    }

    public long getPurgeTick(float halfLifeTicks, float epsilon) {
        if (endTick == -1L) return Long.MAX_VALUE; //[cite: 3]
        return endTick + (long) Math.max(0, halfLifeTicks * (Math.log(epsilon / baseEmission) / Math.log(0.5))); //[cite: 3]
    }
    
    public boolean isActive() {
        return endTick == -1L; //[cite: 3]
    }
}