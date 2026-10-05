package net.kb150.dragoncolonies.network.message;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import net.kb150.dragoncolonies.DragonColonies;
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

public class SetDragonAssignmentMessage {
    private final BlockPos roostPos;
    private final UUID dragonId;
    private final String mode;
    private final int citizenId;

    public SetDragonAssignmentMessage(BlockPos roostPos, UUID dragonId, String mode, int citizenId) {
        this.roostPos = roostPos;
        this.dragonId = dragonId;
        this.mode = mode;
        this.citizenId = citizenId;
    }

    public static void encode(SetDragonAssignmentMessage msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.roostPos);
        buf.writeUUID(msg.dragonId);
        buf.writeUtf(msg.mode);
        buf.writeInt(msg.citizenId);
    }

    public static SetDragonAssignmentMessage decode(FriendlyByteBuf buf) {
        return new SetDragonAssignmentMessage(
                buf.readBlockPos(),
                buf.readUUID(),
                buf.readUtf(32),
                buf.readInt()
        );
    }

    public static void handle(SetDragonAssignmentMessage msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            IColony colony = IMinecoloniesAPI.getInstance().getColonyManager().getColonyByPosFromWorld(player.serverLevel(), msg.roostPos);
            if (colony == null) return;

            IBuilding building = colony.getServerBuildingManager().getBuilding(msg.roostPos);
            if (!(building instanceof BuildingDragonRoost roost)) return;

            DragonStorageModule storage = roost.getStorageModule();
            if (storage == null) return;

            Optional<CompoundTag> opt = storage.getDragonByRoostId(msg.dragonId);
            if (opt.isEmpty()) opt = storage.getDragonByUUID(msg.dragonId);

            if (opt.isPresent()) {
                CompoundTag tag = opt.get();
                tag.putString(DragonStorageModule.TAG_ASSIGNMENT_MODE, msg.mode);
                tag.putInt(DragonStorageModule.TAG_ASSIGNED_CITIZEN_ID, msg.citizenId);

                // Altes Legacy-Feld aktiv entsorgen, damit keine Datenleichen im NBT verbleiben
                tag.remove("DragonColonies_AllowBreeding");
                storage.markDirty();

                DragonColonies.LOGGER.debug("Dragon assignment updated: RoostID={}, Mode={}, CitizenID={}",
                        msg.dragonId, msg.mode, msg.citizenId);
            }
        });
        ctx.setPacketHandled(true);
    }
}