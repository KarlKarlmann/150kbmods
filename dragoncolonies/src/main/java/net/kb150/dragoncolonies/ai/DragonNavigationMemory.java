package net.kb150.dragoncolonies.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

public class DragonNavigationMemory extends SavedData {

    private static final String DATA_NAME = "dragoncolonies_dismount_memory";
    private final Set<BlockPos> dismountZones = new HashSet<>();

    public DragonNavigationMemory() {}

    public static DragonNavigationMemory get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            DragonNavigationMemory::load,
            DragonNavigationMemory::new,
            DATA_NAME
        );
    }

    public static DragonNavigationMemory load(CompoundTag tag) {
        DragonNavigationMemory memory = new DragonNavigationMemory();
        ListTag list = tag.getList("DismountZones", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag posTag = list.getCompound(i);
            memory.dismountZones.add(NbtUtils.readBlockPos(posTag));
        }
        return memory;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (BlockPos pos : dismountZones) {
            list.add(NbtUtils.writeBlockPos(pos));
        }
        tag.put("DismountZones", list);
        return tag;
    }

    /**
     * Ist exakt dieser Block gesperrt?
     */
    public boolean isKnownDismountZone(BlockPos pos) {
        return pos != null && this.dismountZones.contains(pos);
    }

    /**
     * Registriert einen einzelnen Problem-Block.
     */
    public void rememberAsDismountZone(BlockPos pos) {
        if (pos != null && this.dismountZones.add(pos.immutable())) {
            this.setDirty();
            System.out.println("[DRAGON-MEMORY] Block " + pos.toShortString() + " als Absteig-Zone gelernt!");
        }
    }

    /**
     * Sensor: Liegt im Umkreis von 'radius' Blöcken irgendeine bekannte Falle?
     */
    public boolean isNearProblemZone(BlockPos centerPos, int radius) {
        if (centerPos == null || this.dismountZones.isEmpty()) return false;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -2; y <= 2; y++) {
                    if (this.dismountZones.contains(centerPos.offset(x, y, z))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}