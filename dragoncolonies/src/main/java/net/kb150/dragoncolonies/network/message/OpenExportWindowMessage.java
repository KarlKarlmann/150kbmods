package net.kb150.dragoncolonies.network.message;

import com.ldtteam.blockui.BOScreen; // <-- HIER IST DIE ÄNDERUNG
import net.kb150.dragoncolonies.client.gui.WindowDragonExport;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class OpenExportWindowMessage {
    private final BlockPos roostPos;
    private final UUID dragonId;
    private final CompoundTag offersTag;

    public OpenExportWindowMessage(BlockPos roostPos, UUID dragonId, CompoundTag offersTag) {
        this.roostPos = roostPos;
        this.dragonId = dragonId;
        this.offersTag = offersTag;
    }

    public static void encode(OpenExportWindowMessage message, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(message.roostPos);
        buffer.writeUUID(message.dragonId);
        buffer.writeNbt(message.offersTag);
    }

    public static OpenExportWindowMessage decode(FriendlyByteBuf buffer) {
        return new OpenExportWindowMessage(buffer.readBlockPos(), buffer.readUUID(), buffer.readNbt());
    }

    public static void handle(OpenExportWindowMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // Minecolonies schließt das alte Fenster automatisch, wenn ein neues SetScreen gefeuert wird.
            Minecraft.getInstance().setScreen(new BOScreen(new WindowDragonExport(message.roostPos, message.dragonId, message.offersTag))); // <-- UND HIER
        });
        context.setPacketHandled(true);
    }
}