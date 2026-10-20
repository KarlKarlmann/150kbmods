package net.kb150.woerp.capability;

import net.minecraft.nbt.CompoundTag;

public class ChunkRadiation implements IChunkRadiation {
    private float ambientRadiation = 0.0f;
    private long lastUpdateTick = 0L;

    @Override
    public float getAmbient() { return ambientRadiation; }
    
    @Override
    public void setAmbient(float value) { 
        // 500.0f spiegelt das visuelle Maximum des Shaders; fängt Runaways sofort ab
        this.ambientRadiation = Math.max(0.0f, Math.min(500.0f, value)); 
    }
    
    @Override
    public long getLastUpdateTick() { return lastUpdateTick; }
    
    @Override
    public void setLastUpdateTick(long tick) { this.lastUpdateTick = tick; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("ambient", ambientRadiation);
        tag.putLong("lastUpdate", lastUpdateTick);
        return tag;
    }

    public void load(CompoundTag tag) {
        // Bereinigt korrumpierte Altdaten über 500 beim Betreten von Chunks
        this.ambientRadiation = Math.max(0.0f, Math.min(500.0f, tag.getFloat("ambient")));
        this.lastUpdateTick = tag.getLong("lastUpdate");
    }
}