package net.kb150.woerp.network;

import net.kb150.woerp.client.ClientRadiationData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SyncRadiationPacket {
    private final float radiation;
    private final List<BlockPos> emitterPositions;

    public SyncRadiationPacket(float radiation, List<BlockPos> emitterPositions) {
        this.radiation = radiation;
        this.emitterPositions = emitterPositions;
    }

    public SyncRadiationPacket(FriendlyByteBuf buf) {
        this.radiation = buf.readFloat();
        int count = buf.readVarInt();
        this.emitterPositions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            this.emitterPositions.add(buf.readBlockPos());
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(this.radiation);
        buf.writeVarInt(this.emitterPositions.size());
        for (BlockPos pos : this.emitterPositions) {
            buf.writeBlockPos(pos);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> 
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRadiationData.setSnapshot(radiation, emitterPositions))
        );
        ctx.get().setPacketHandled(true);
    }
}