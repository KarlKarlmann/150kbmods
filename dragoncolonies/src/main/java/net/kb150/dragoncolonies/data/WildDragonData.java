package net.kb150.dragoncolonies.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// Persists escaped wild dragons mapped to target chunk coordinates.
// Enables lazy instantiation when chunks load naturally without triggering worldgen lag.
public class WildDragonData extends SavedData {

    private static final String DATA_NAME = "dragoncolonies_wild_dragons";

    public static class PendingWildDragon {
        public final long chunkKey;
        public final int targetX;
        public final int targetZ;
        public final CompoundTag dragonData;

        public PendingWildDragon(long chunkKey, int targetX, int targetZ, CompoundTag dragonData) {
            this.chunkKey = chunkKey;
            this.targetX = targetX;
            this.targetZ = targetZ;
            this.dragonData = dragonData;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("ChunkKey", this.chunkKey);
            tag.putInt("TargetX", this.targetX);
            tag.putInt("TargetZ", this.targetZ);
            tag.put("DragonData", this.dragonData);
            return tag;
        }

        public static PendingWildDragon load(CompoundTag tag) {
            return new PendingWildDragon(
                    tag.getLong("ChunkKey"),
                    tag.getInt("TargetX"),
                    tag.getInt("TargetZ"),
                    tag.getCompound("DragonData")
            );
        }
    }

    private final List<PendingWildDragon> pendingDragons = new ArrayList<>();

    public WildDragonData() {}

    public WildDragonData(CompoundTag tag) {
        if (tag.contains("PendingDragons", Tag.TAG_LIST)) {
            ListTag list = tag.getList("PendingDragons", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                this.pendingDragons.add(PendingWildDragon.load(list.getCompound(i)));
            }
        }
    }

    public static WildDragonData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(WildDragonData::new, WildDragonData::new, DATA_NAME);
    }

    public synchronized void addDragon(long chunkKey, int targetX, int targetZ, CompoundTag dragonData) {
        this.pendingDragons.add(new PendingWildDragon(chunkKey, targetX, targetZ, dragonData));
        this.setDirty();
    }

    public synchronized List<PendingWildDragon> popDragonsForChunk(long chunkKey) {
        List<PendingWildDragon> matched = new ArrayList<>();
        Iterator<PendingWildDragon> it = this.pendingDragons.iterator();
        while (it.hasNext()) {
            PendingWildDragon p = it.next();
            if (p.chunkKey == chunkKey) {
                matched.add(p);
                it.remove();
            }
        }
        if (!matched.isEmpty()) {
            this.setDirty();
        }
        return matched;
    }

    @Override
    public CompoundTag save(CompoundTag compound) {
        ListTag list = new ListTag();
        for (PendingWildDragon dragon : this.pendingDragons) {
            list.add(dragon.save());
        }
        compound.put("PendingDragons", list);
        return compound;
    }
}