package net.kb150.dragoncolonies.util;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

public final class ColonyRelationHelper {

    // Sämtliche Mod-Tags, die die Bindung an einen Hort oder eine Kolonie definieren
    private static final String[] COLONY_TAGS = {
        "DragonColonies_RoostPos",
        "DragonColonies_RoostDragonID",
        "DragonColonies_GuardDeployed",
        "DragonColonies_OrphanTicks",
        "DragonColonies_GuardUUID",
        "DragonColonies_FleeingTicks",
        "DragonColonies_ColonyId"
    };

    private ColonyRelationHelper() {}

    // Entkoppelt Entity-Instanzen restlos von Kolonie-Systemen (verhindert Geister-Bindungen)
    public static void cleanColonyData(DragonBase dragon) {
        if (dragon != null) {
            cleanColonyData(dragon.getPersistentData());
        }
    }

    public static void cleanColonyData(CompoundTag tag) {
        if (tag == null) return;
        for (String key : COLONY_TAGS) {
            tag.remove(key);
        }
    }

    public static int getDragonColonyId(DragonBase dragon) {
        if (dragon == null) return -1;

        CompoundTag data = dragon.getPersistentData();
        if (data.contains("DragonColonies_ColonyId")) {
            return data.getInt("DragonColonies_ColonyId");
        }

        if (data.contains("DragonColonies_RoostPos") && dragon.level() instanceof ServerLevel serverLevel) {
            BlockPos roostPos = BlockPos.of(data.getLong("DragonColonies_RoostPos"));
            IColony colony = IMinecoloniesAPI.getInstance().getColonyManager().getColonyByPosFromWorld(serverLevel, roostPos);
            if (colony != null) {
                int id = colony.getID();
                data.putInt("DragonColonies_ColonyId", id);
                return id;
            }
        }

        return -1;
    }

    public static boolean isSameColony(AbstractEntityCitizen citizen, DragonBase dragon) {
        if (citizen == null || dragon == null) return false;

        int citizenColonyId = citizen.getCitizenColonyHandler() != null 
                ? citizen.getCitizenColonyHandler().getColonyId() 
                : -1;
        if (citizenColonyId <= 0) return false;

        int dragonColonyId = getDragonColonyId(dragon);
        return dragonColonyId > 0 && citizenColonyId == dragonColonyId;
    }
}