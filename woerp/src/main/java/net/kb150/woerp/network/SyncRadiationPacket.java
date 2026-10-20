package net.kb150.woerp.network;

import net.kb150.woerp.Woerp;
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
    private final float ambient;
    private final float bodyDose;
    private final List<BlockPos> emitterPositions;

    public SyncRadiationPacket(float ambient, float bodyDose, List<BlockPos> emitterPositions) {
        this.ambient = ambient;
        this.bodyDose = bodyDose;
        this.emitterPositions = emitterPositions;
    }

    public SyncRadiationPacket(FriendlyByteBuf buf) {
        this.ambient = buf.readFloat();
        this.bodyDose = buf.readFloat();
        int count = buf.readVarInt();
        this.emitterPositions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) this.emitterPositions.add(buf.readBlockPos());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(this.ambient);
        buf.writeFloat(this.bodyDose);
        buf.writeVarInt(this.emitterPositions.size());
        for (BlockPos pos : this.emitterPositions) buf.writeBlockPos(pos);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> 
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                Woerp.LOGGER.info("[WOERP-NET] S2C Sync: Ambient={}, BodyDose={}, Emitter={}", ambient, bodyDose, emitterPositions.size());
                ClientRadiationData.setSnapshot(ambient, bodyDose, emitterPositions);
            })
        );
        ctx.get().setPacketHandled(true);
    }
}