package net.kb150.woerp.capability;

public interface IChunkRadiation {
    float getAmbient();
    void setAmbient(float value);
    long getLastUpdateTick();
    void setLastUpdateTick(long tick);
}