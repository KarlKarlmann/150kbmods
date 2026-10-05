package net.kb150.dragoncolonies.network.message;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import net.kb150.dragoncolonies.buildings.BuildingDragonRoost;
import net.kb150.dragoncolonies.buildings.modules.DragonStorageModule;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

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

            // Zentraler Aufruf ohne lokales NBT-Gefummel
            storage.deployDragon(message.dragonId, level, roost.getPosition(), null);
        });
        context.setPacketHandled(true);
    }
}