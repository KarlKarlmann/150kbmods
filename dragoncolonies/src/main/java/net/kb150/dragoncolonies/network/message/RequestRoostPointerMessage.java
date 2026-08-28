package net.kb150.dragoncolonies.network.message;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import net.kb150.dragoncolonies.buildings.BuildingDragonRoost;
import net.kb150.dragoncolonies.items.ItemRoostPointer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paket vom Client an den Server: Klick auf "In Obhut rufen" fordert den Hort-Leitstab an.
 */
public class RequestRoostPointerMessage {

    private final BlockPos roostPos;

    public RequestRoostPointerMessage(BlockPos roostPos) {
        this.roostPos = roostPos;
    }

    public static void encode(RequestRoostPointerMessage message, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(message.roostPos);
    }

    public static RequestRoostPointerMessage decode(FriendlyByteBuf buffer) {
        return new RequestRoostPointerMessage(buffer.readBlockPos());
    }

    public static void handle(RequestRoostPointerMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                IColony colony = IMinecoloniesAPI.getInstance().getColonyManager().getColonyByPosFromWorld(player.serverLevel(), message.roostPos);
                if (colony != null) {
                    IBuilding building = colony.getServerBuildingManager().getBuilding(message.roostPos);
                    if (building instanceof BuildingDragonRoost) {
                        
                        // Erzeuge den gebundenen Leitstab und gib ihn dem Spieler ins Inventar
                        ItemStack pointerStack = ItemRoostPointer.createBoundPointer(message.roostPos);
                        
                        if (!player.getInventory().add(pointerStack)) {
                            player.drop(pointerStack, false);
                        }
                        
                        player.sendSystemMessage(Component.literal("§a[DragonColonies] §fHort-Leitstab erhalten. Klicke auf deinen Drachen, um ihn in den Hort zu rufen!"));
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }
}