package net.kb150.reward_box.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class PacketSyncBoxData {
    public final Map<String, String[]> textureMap;

    public PacketSyncBoxData(Map<String, String[]> textureMap) {
        this.textureMap = textureMap;
    }

    public PacketSyncBoxData(FriendlyByteBuf buf) {
        this.textureMap = new HashMap<>();
        int size = buf.readVarInt();
        for (int i = 0; i < size; i++) {
            this.textureMap.put(buf.readUtf(), new String[]{buf.readUtf(), buf.readUtf()});
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(textureMap.size());
        for (Map.Entry<String, String[]> entry : textureMap.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeUtf(entry.getValue()[0]);
            buf.writeUtf(entry.getValue()[1] != null ? entry.getValue()[1] : "");
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                net.kb150.reward_box.client.cache.ClientBoxCache.update(this.textureMap);
            });
        });
        ctx.get().setPacketHandled(true);
        return true;
    }
}