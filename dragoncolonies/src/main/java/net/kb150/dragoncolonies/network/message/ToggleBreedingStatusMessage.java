package net.kb150.dragoncolonies.network.message;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import net.kb150.dragoncolonies.buildings.BuildingDragonRoost;
import net.kb150.dragoncolonies.buildings.modules.DragonStorageModule;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Teilt dem Server mit, dass der "Zucht zulassen"-Knopf für einen bestimmten Drachen gedrückt wurde.
 */
public class ToggleBreedingStatusMessage {
    private final BlockPos roostPos;
    private final UUID dragonId;

    public ToggleBreedingStatusMessage(BlockPos roostPos, UUID dragonId) {
        this.roostPos = roostPos;
        this.dragonId = dragonId;
    }

    public static void encode(ToggleBreedingStatusMessage message, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(message.roostPos);
        buffer.writeUUID(message.dragonId);
    }

    public static ToggleBreedingStatusMessage decode(FriendlyByteBuf buffer) {
        return new ToggleBreedingStatusMessage(buffer.readBlockPos(), buffer.readUUID());
    }

    public static void handle(ToggleBreedingStatusMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
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
                CompoundTag tag = dragonOpt.get();
                boolean currentState = tag.getBoolean("DragonColonies_AllowBreeding");
                tag.putBoolean("DragonColonies_AllowBreeding", !currentState);
                storage.markDirty();
            }
        });
        context.setPacketHandled(true);
    }
}