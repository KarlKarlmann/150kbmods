package net.kb150.dragoncolonies.network.message;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import net.kb150.dragoncolonies.buildings.BuildingDragonRoost;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Paket zum endgültigen Löschen/Freilassen eines Drachens aus dem Speichermodul.
 */
public class ReleaseDragonMessage {
    private final BlockPos roostPos;
    private final UUID dragonId;

    public ReleaseDragonMessage(BlockPos roostPos, UUID dragonId) {
        this.roostPos = roostPos;
        this.dragonId = dragonId;
    }

    public static void encode(ReleaseDragonMessage message, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(message.roostPos);
        buffer.writeUUID(message.dragonId);
    }

    public static ReleaseDragonMessage decode(FriendlyByteBuf buffer) {
        return new ReleaseDragonMessage(buffer.readBlockPos(), buffer.readUUID());
    }

    public static void handle(ReleaseDragonMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                IColony colony = IMinecoloniesAPI.getInstance().getColonyManager().getColonyByPosFromWorld(player.serverLevel(), message.roostPos);
                if (colony != null) {
                    IBuilding building = colony.getServerBuildingManager().getBuilding(message.roostPos);
                    if (building instanceof BuildingDragonRoost roost) {
                        roost.getStorageModule().removeDragon(message.dragonId);
                        player.sendSystemMessage(Component.literal("§c[DragonColonies] §fDer Drache wurde freigelassen."));
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }
}