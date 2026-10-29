package net.kb150.woerp.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenMutationStudioPacket {
    public OpenMutationStudioPacket() {}
    public OpenMutationStudioPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public static void handle(OpenMutationStudioPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Verhindert ClassNotFoundException auf Dedicated Servern
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                net.minecraft.client.Minecraft.getInstance().setScreen(new net.kb150.woerp.client.screen.MutationStudioScreen());
            });
        });
        ctx.get().setPacketHandled(true);
    }
}