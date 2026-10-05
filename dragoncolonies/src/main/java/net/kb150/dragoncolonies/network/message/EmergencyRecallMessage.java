package net.kb150.dragoncolonies.network.message;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import net.kb150.dragoncolonies.buildings.BuildingDragonRoost;
import net.kb150.dragoncolonies.buildings.modules.DragonStorageModule;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Notfall-Rückruf: Delegiert vollständig an die zentrale Lifecycle-Methode
 * emergencyRecall() im DragonStorageModule.
 */
public class EmergencyRecallMessage {
    private final BlockPos roostPos;
    private final UUID dragonId;

    public EmergencyRecallMessage(BlockPos roostPos, UUID dragonId) {
        this.roostPos = roostPos;
        this.dragonId = dragonId;
    }

    public static void encode(EmergencyRecallMessage message, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(message.roostPos);
        buffer.writeUUID(message.dragonId);
    }

    public static EmergencyRecallMessage decode(FriendlyByteBuf buffer) {
        return new EmergencyRecallMessage(buffer.readBlockPos(), buffer.readUUID());
    }

    public static void handle(EmergencyRecallMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            IColony colony = IMinecoloniesAPI.getInstance().getColonyManager().getColonyByPosFromWorld(player.serverLevel(), message.roostPos);
            if (colony == null) return;

            IBuilding building = colony.getServerBuildingManager().getBuilding(message.roostPos);
            if (!(building instanceof BuildingDragonRoost roost)) return;

            DragonStorageModule storage = roost.getStorageModule();
            if (storage == null) return;

            // Zentraler Notfall-Rückruf: Entität sichern/verwerfen & Hort-Status bereinigen
            if (storage.emergencyRecall(message.dragonId, player.server)) {
                player.sendSystemMessage(Component.literal("§a[DragonColonies] §fDer Drache wurde per Notfall-Rückruf in den Hort zurückgeholt."));
            }
        });
        context.setPacketHandled(true);
    }
}