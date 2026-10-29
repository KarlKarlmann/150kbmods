package net.kb150.dragoncolonies.util;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

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

        if (dragon.level() instanceof ServerLevel serverLevel) {
            // 1. Aus Roost-Position ermitteln
            if (data.contains("DragonColonies_RoostPos")) {
                BlockPos roostPos = BlockPos.of(data.getLong("DragonColonies_RoostPos"));
                IColony colony = IMinecoloniesAPI.getInstance().getColonyManager().getColonyByPosFromWorld(serverLevel, roostPos);
                if (colony != null) {
                    int id = colony.getID();
                    data.putInt("DragonColonies_ColonyId", id);
                    return id;
                }
            }

            // 2. Aus zugewiesener Wache ermitteln
            if (data.hasUUID("DragonColonies_GuardUUID")) {
                Entity guard = serverLevel.getEntity(data.getUUID("DragonColonies_GuardUUID"));
                if (guard instanceof AbstractEntityCitizen citizen && citizen.getCitizenColonyHandler() != null) {
                    int id = citizen.getCitizenColonyHandler().getColonyId();
                    if (id > 0) {
                        data.putInt("DragonColonies_ColonyId", id);
                        return id;
                    }
                }
            }

            // 3. Aus aktuellem Passagier (Wache) ermitteln
            if (dragon.getFirstPassenger() instanceof AbstractEntityCitizen citizen && citizen.getCitizenColonyHandler() != null) {
                int id = citizen.getCitizenColonyHandler().getColonyId();
                if (id > 0) {
                    data.putInt("DragonColonies_ColonyId", id);
                    return id;
                }
            }
        }

        return -1;
    }

    // Prueft, ob Buerger und Drache zur selben Kolonie gehoeren
    public static boolean isSameColony(AbstractEntityCitizen citizen, DragonBase dragon) {
        if (citizen == null || dragon == null) return false;

        int citizenColonyId = citizen.getCitizenColonyHandler() != null 
                ? citizen.getCitizenColonyHandler().getColonyId() 
                : -1;
        if (citizenColonyId <= 0) return false;

        int dragonColonyId = getDragonColonyId(dragon);
        return dragonColonyId > 0 && citizenColonyId == dragonColonyId;
    }

    // Prueft, ob zwei Drachen zur selben Kolonie gehoeren
    public static boolean isSameColony(DragonBase dragon1, DragonBase dragon2) {
        if (dragon1 == null || dragon2 == null) return false;

        int id1 = getDragonColonyId(dragon1);
        int id2 = getDragonColonyId(dragon2);
        return id1 > 0 && id1 == id2;
    }

    // Prueft, ob ein Spieler Rechte in der Kolonie des Drachen besitzt (Besitzer/Mitglied)
    public static boolean isPlayerInColony(Player player, DragonBase dragon) {
        if (player == null || dragon == null) return false;

        int dragonColonyId = getDragonColonyId(dragon);
        if (dragonColonyId <= 0) return false;

        if (dragon.level() instanceof ServerLevel serverLevel) {
            IColony colony = IMinecoloniesAPI.getInstance().getColonyManager().getColonyByWorld(dragonColonyId, serverLevel);
            if (colony != null) {
                if (player.getUUID().equals(colony.getPermissions().getOwner())) {
                    return true;
                }
                try {
                    return colony.getPermissions().hasPermission(player, Action.ACCESS_HUTS);
                } catch (Throwable ignored) {
                    return false;
                }
            }
        }
        return false;
    }
}