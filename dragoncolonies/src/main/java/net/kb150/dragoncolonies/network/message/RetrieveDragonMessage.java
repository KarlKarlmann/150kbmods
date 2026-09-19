package net.kb150.dragoncolonies.network.message;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import net.kb150.dragoncolonies.buildings.BuildingDragonRoost;
import net.kb150.dragoncolonies.buildings.modules.DragonStorageModule;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Netzwerkpaket zum Entnehmen eines Drachens aus dem Hort in die Spielwelt.
 * Erzeugt eine frische Entity-UUID, während die unveränderliche RoostDragonID
 * als Anker im Storage hinterlegt bleibt.
 */
public class RetrieveDragonMessage {

    private final BlockPos roostPos;
    private final UUID dragonId;

    public RetrieveDragonMessage(BlockPos roostPos, UUID dragonId) {
        this.roostPos = roostPos;
        this.dragonId = dragonId;
    }

    public static void encode(RetrieveDragonMessage message, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(message.roostPos);
        buffer.writeUUID(message.dragonId);
    }

    public static RetrieveDragonMessage decode(FriendlyByteBuf buffer) {
        return new RetrieveDragonMessage(buffer.readBlockPos(), buffer.readUUID());
    }

    public static void handle(RetrieveDragonMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            ServerLevel level = player.serverLevel();
            IColony colony = IMinecoloniesAPI.getInstance().getColonyManager().getColonyByPosFromWorld(level, message.roostPos);
            if (colony == null) return;

            IBuilding building = colony.getServerBuildingManager().getBuilding(message.roostPos);
            if (!(building instanceof BuildingDragonRoost roost)) return;

            DragonStorageModule storage = roost.getStorageModule();
            if (storage == null) return;

            Optional<CompoundTag> optDragonTag;
            if (message.dragonId.equals(new UUID(0, 0))) {
                optDragonTag = storage.getAllDragons().stream()
                        .filter(t -> !t.getBoolean(DragonStorageModule.TAG_DEPLOYED) && !t.getBoolean(DragonStorageModule.TAG_IS_DEAD))
                        .findFirst();
            } else {
                optDragonTag = storage.getDragonByRoostId(message.dragonId);
                if (optDragonTag.isEmpty()) {
                    optDragonTag = storage.getDragonByUUID(message.dragonId);
                }
            }

            if (optDragonTag.isPresent()) {
                CompoundTag dragonTag = optDragonTag.get();

                // 1. RoostDragonID sicherstellen
                UUID roostDragonId = dragonTag.hasUUID(DragonStorageModule.TAG_ROOST_DRAGON_ID)
                        ? dragonTag.getUUID(DragonStorageModule.TAG_ROOST_DRAGON_ID)
                        : UUID.randomUUID();
                dragonTag.putUUID(DragonStorageModule.TAG_ROOST_DRAGON_ID, roostDragonId);

                // 2. Frische Entity-UUID erzeugen (verhindert Engine-UUID-Kollisionen im Chunk-Lader)
                UUID newEntityUuid = UUID.randomUUID();
                dragonTag.putUUID("UUID", newEntityUuid);

                if (!dragonTag.contains("id") && dragonTag.contains("DragonType")) {
                    dragonTag.putString("id", "bookofdragons:" + dragonTag.getString("DragonType").toLowerCase());
                }

                // 3. Status und aktive Entity-UUID im Hort registrieren
                storage.setDeployedStatus(roostDragonId, true, newEntityUuid);
                storage.markDirty();

                // 4. Entität instanziieren und Spawntags stempeln
				Entity entity = EntityType.loadEntityRecursive(dragonTag, level, (e) -> {
					e.moveTo(roost.getPosition().getX() + 0.5, roost.getPosition().getY() + 1.0, roost.getPosition().getZ() + 0.5, 0, 0);

					e.getPersistentData().putLong("DragonColonies_RoostPos", roost.getPosition().asLong());
					e.getPersistentData().putUUID("DragonColonies_RoostDragonID", roostDragonId);
					
					// NEU: Wachen-Kopplung beim manuellen Rausholen entfernen
					e.getPersistentData().remove("DragonColonies_GuardDeployed");
					e.getPersistentData().remove("DragonColonies_GuardUUID");
					return e;
				});

                if (entity != null) {
                    level.addFreshEntity(entity);
                }
            }
        });
        context.setPacketHandled(true);
    }
}