package net.kb150.woerp.radiation;

import net.kb150.woerp.config.WoerpConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

public class EmitterSavedData extends SavedData {
    private final List<RadiationEmitter> emitters = new ArrayList<>();

    // Lädt die Daten aus dem NBT
    public static EmitterSavedData load(CompoundTag tag) {
        EmitterSavedData data = new EmitterSavedData();
        ListTag list = tag.getList("emitters", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            data.emitters.add(new RadiationEmitter(list.getCompound(i)));
        }
        return data;
    }

    // Speichert die Daten ins NBT
    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (RadiationEmitter emitter : emitters) {
            list.add(emitter.save());
        }
        tag.put("emitters", list);
        return tag;
    }

    public void addEmitter(RadiationEmitter emitter) {
        this.emitters.add(emitter);
        this.setDirty();
    }

    public List<RadiationEmitter> getEmitters() {
        return this.emitters;
    }

    // Wird z.B. beim Weltspeichern aufgerufen, um Dateileichen zu entfernen
    public void cleanupDeadEmitters(long currentTick) {
        int initialSize = emitters.size();
        float halfLife = WoerpConfig.HALF_LIFE_TICKS.get().floatValue();
        float epsilon = WoerpConfig.EPSILON.get().floatValue();

        emitters.removeIf(e -> currentTick >= e.getPurgeTick(halfLife, epsilon));

        if (emitters.size() != initialSize) {
            this.setDirty();
        }
    }

    // Helfer-Methode, um die Instanz für ein Level zu holen
    public static EmitterSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                EmitterSavedData::load,
                EmitterSavedData::new,
                "woerp_emitters"
        );
    }
}