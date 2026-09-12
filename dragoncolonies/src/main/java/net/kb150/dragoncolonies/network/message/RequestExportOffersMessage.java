package net.kb150.dragoncolonies.network.message;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import net.kb150.dragoncolonies.buildings.BuildingDragonRoost;
import net.kb150.dragoncolonies.buildings.modules.DragonStorageModule;
import net.kb150.dragoncolonies.export.DragonExportManager;
import net.kb150.dragoncolonies.network.DragonColoniesNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public class RequestExportOffersMessage {
    private final BlockPos roostPos;
    private final UUID dragonId;

    public RequestExportOffersMessage(BlockPos roostPos, UUID dragonId) {
        this.roostPos = roostPos;
        this.dragonId = dragonId;
    }

    public static void encode(RequestExportOffersMessage message, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(message.roostPos);
        buffer.writeUUID(message.dragonId);
    }

    public static RequestExportOffersMessage decode(FriendlyByteBuf buffer) {
        return new RequestExportOffersMessage(buffer.readBlockPos(), buffer.readUUID());
    }

    public static void handle(RequestExportOffersMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
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

            Optional<CompoundTag> dragonOpt = storage.getDragonByRoostId(message.dragonId);
            if (dragonOpt.isEmpty()) dragonOpt = storage.getDragonByUUID(message.dragonId);

            if (dragonOpt.isPresent()) {
                CompoundTag dragonNbt = dragonOpt.get();
                // Serverseitige Berechnung der Angebote
                CompoundTag offers = DragonExportManager.generateOffers(player.serverLevel(), message.dragonId, dragonNbt);

                // Zurück an den anfragenden Client schicken
                DragonColoniesNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenExportWindowMessage(message.roostPos, message.dragonId, offers));
            }
        });
        context.setPacketHandled(true);
    }
}