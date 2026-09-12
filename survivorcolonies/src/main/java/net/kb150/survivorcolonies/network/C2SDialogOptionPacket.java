package net.kb150.survivorcolonies.network;

import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SDialogOptionPacket {
    private final int survivorId;
    private final int optionId;
    private final int trustDelta;
    private final boolean isOnce;

    public C2SDialogOptionPacket(int survivorId, int optionId, int trustDelta, boolean isOnce) {
        this.survivorId = survivorId;
        this.optionId = optionId;
        this.trustDelta = trustDelta;
        this.isOnce = isOnce;
    }

    public C2SDialogOptionPacket(FriendlyByteBuf buf) {
        this.survivorId = buf.readInt();
        this.optionId = buf.readInt();
        this.trustDelta = buf.readInt();
        this.isOnce = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.survivorId);
        buf.writeInt(this.optionId);
        buf.writeInt(this.trustDelta);
        buf.writeBoolean(this.isOnce);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Entity entity = player.level().getEntity(this.survivorId);
            if (entity instanceof SurvivorEntity survivor) {
                
                // 1. Spam-Schutz-Check (wird hier auf dem Server nochmal hart geprüft)
                if (this.isOnce && survivor.hasUsedOption(this.optionId)) {
                    return; 
                }

                // 2. Vertrauen anwenden
                if (this.trustDelta != 0) {
                    survivor.addTrust(this.trustDelta);
                }

                // 3. Option dauerhaft als verbraucht markieren
                if (this.isOnce) {
                    survivor.markOptionUsed(this.optionId);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}