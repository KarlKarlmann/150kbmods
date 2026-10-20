package net.kb150.woerp.capability;

import net.minecraft.nbt.CompoundTag;

public class PlayerRadiation implements IPlayerRadiation {
    private float bodyDose = 0.0f;

    @Override
    public float getBodyDose() { return bodyDose; }

    @Override
    public void setBodyDose(float value) { 
        // 500.0f spiegelt das visuelle Maximum der Post-Chain; verhindert NBT-Aufblaehung bei extremen Schadenswerten
        this.bodyDose = Math.max(0.0f, Math.min(500.0f, value)); 
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("bodyDose", bodyDose);
        return tag;
    }

    public void load(CompoundTag tag) {
        this.bodyDose = Math.max(0.0f, Math.min(500.0f, tag.getFloat("bodyDose")));
    }
}