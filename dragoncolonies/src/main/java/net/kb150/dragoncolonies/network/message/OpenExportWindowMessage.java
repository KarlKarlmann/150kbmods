package net.kb150.dragoncolonies.network.message;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.UUID;
import java.util.function.Supplier;

public class OpenExportWindowMessage {
    public final BlockPos roostPos;
    public final UUID dragonId;
    public final CompoundTag offersTag;

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
            // Führt den Code NUR auf dem physischen Client aus
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(message));
        });
        context.setPacketHandled(true);
    }

    // Isoliert den Client-Only Code, damit der Server ihn beim Laden der Klasse nicht verifiziert
    @OnlyIn(Dist.CLIENT)
    private static void handleClient(OpenExportWindowMessage message) {
        net.minecraft.client.Minecraft.getInstance().setScreen(
            new com.ldtteam.blockui.BOScreen(
                new net.kb150.dragoncolonies.client.gui.WindowDragonExport(message.roostPos, message.dragonId, message.offersTag)
            )
        );
    }
}