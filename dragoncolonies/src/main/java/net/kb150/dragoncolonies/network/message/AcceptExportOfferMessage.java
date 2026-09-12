package net.kb150.dragoncolonies.network.message;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import net.kb150.dragoncolonies.buildings.BuildingDragonRoost;
import net.kb150.dragoncolonies.buildings.modules.DragonStorageModule;
import net.kb150.dragoncolonies.export.DragonExportManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public class AcceptExportOfferMessage {
    private final BlockPos roostPos;
    private final UUID dragonId;
    private final String factionId;

    public AcceptExportOfferMessage(BlockPos roostPos, UUID dragonId, String factionId) {
        this.roostPos = roostPos;
        this.dragonId = dragonId;
        this.factionId = factionId;
    }

    public static void encode(AcceptExportOfferMessage message, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(message.roostPos);
        buffer.writeUUID(message.dragonId);
        buffer.writeUtf(message.factionId);
    }

    public static AcceptExportOfferMessage decode(FriendlyByteBuf buffer) {
        return new AcceptExportOfferMessage(buffer.readBlockPos(), buffer.readUUID(), buffer.readUtf());
    }

    public static void handle(AcceptExportOfferMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
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

                // Neu-Berechnung auf dem Server, um Cheat-Injection im Client zu verhindern
                CompoundTag offersTag = DragonExportManager.generateOffers(player.serverLevel(), message.dragonId, dragonNbt);
                ListTag list = offersTag.getList("Offers", Tag.TAG_COMPOUND);
                CompoundTag acceptedOffer = null;
                for (int i = 0; i < list.size(); i++) {
                    if (list.getCompound(i).getString("FactionId").equals(message.factionId)) {
                        acceptedOffer = list.getCompound(i);
                        break;
                    }
                }

                if (acceptedOffer != null) {
                    // Items ins Inventar legen oder droppen
                    ListTag itemsTag = acceptedOffer.getList("Items", Tag.TAG_COMPOUND);
                    for (int i = 0; i < itemsTag.size(); i++) {
                        ItemStack stack = ItemStack.of(itemsTag.getCompound(i));
                        if (!player.getInventory().add(stack)) {
                            player.drop(stack, false);
                        }
                    }

                    // Sound abspielen (Bling!)
                    player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 1.0f);
                    
                    // Drachen endgültig entfernen
                    storage.removeDragon(message.dragonId);
                    
                    player.sendSystemMessage(Component.literal("§a[DragonColonies] §fDrache erfolgreich an die Fraktion '" + acceptedOffer.getString("FactionName") + "' übergeben. Tribut erhalten!"));
                }
            }
        });
        context.setPacketHandled(true);
    }
}