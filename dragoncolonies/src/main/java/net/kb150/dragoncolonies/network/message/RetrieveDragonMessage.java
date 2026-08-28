package net.kb150.dragoncolonies.network.message;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import net.kb150.dragoncolonies.buildings.BuildingDragonRoost;
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
 * Nachricht vom Client an den Server, um einen Drachen aus dem NBT-Speicher des Hortes physisch in die Welt zu spawnen.
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
            if (player != null) {
                ServerLevel level = player.serverLevel();
                
                IColony colony = IMinecoloniesAPI.getInstance().getColonyManager().getColonyByPosFromWorld(level, message.roostPos);
                if (colony != null) {
                    IBuilding building = colony.getServerBuildingManager().getBuilding(message.roostPos);
                    
                    if (building instanceof BuildingDragonRoost roost) {
                        // 1. Lese NBT-Daten aus dem Speichermodul
                        Optional<CompoundTag> optDragonTag;
                        
                        // TEST-MODUS: Wenn die UUID 0-0-0-0 ist, holen wir einfach den ersten Drachen aus der Liste, der nicht auf Reisen ist
                        if (message.dragonId.equals(new UUID(0, 0))) {
                            optDragonTag = roost.getStorageModule().getAllDragons().stream().filter(t -> !t.getBoolean("Deployed")).findFirst();
                        } else {
                            optDragonTag = roost.getStorageModule().getDragonByUUID(message.dragonId);
                        }
                        
                        if (optDragonTag.isPresent()) {
                            CompoundTag dragonTag = optDragonTag.get();
                            
                            // ZWINGEND: Neue UUID generieren, falls eine Vanilla-Geisterentität den Spawn blockiert
                            UUID newUuid = UUID.randomUUID();
                            dragonTag.putUUID("UUID", newUuid);
                            roost.getStorageModule().markDirty();

                            // Sicherstellen, dass "id" vorhanden ist
                            if (!dragonTag.contains("id") && dragonTag.contains("DragonType")) {
                                dragonTag.putString("id", "bookofdragons:" + dragonTag.getString("DragonType").toLowerCase());
                            }

                            // 2. Erschaffe die Entität aus dem NBT-Datensatz
                            Entity entity = EntityType.loadEntityRecursive(dragonTag, level, (e) -> {
                                e.moveTo(roost.getPosition().getX() + 0.5, roost.getPosition().getY() + 1.0, roost.getPosition().getZ() + 0.5, 0, 0);
                                
                                // QoL: Speichere den Hort, damit der Drache bei Chunk-Unloads nicht verloren geht!
                                e.getPersistentData().putLong("DragonColonies_RoostPos", roost.getPosition().asLong());
                                // Wichtig: Wir setzen hier KEIN "GuardDeployed", damit er draußen bleibt!
                                return e;
                            });

                            if (entity != null) {
                                // 3. Markiere als deployed (mit der neuen UUID) und spawne in die Welt
                                roost.getStorageModule().setDeployedStatus(newUuid, true);
                                level.addFreshEntity(entity);
                            }
                        }
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }
}