package net.kb150.survivorcolonies.network;

import net.kb150.survivorcolonies.data.DialogManager;
import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: the player selected one concrete player reaction and the
 * client predicts the generated NPC reaction it should lead to.
 *
 * The server does NOT trust the predicted target or trust value. It resolves
 * the same graph transition itself and only then applies the generated
 * reaction's trust_delta.
 */
public class C2SDialogOptionPacket {
    private final int survivorId;
    private final int currentNpcReactionId;
    private final int playerReactionId;
    private final int resolvedNpcReactionId;

    public C2SDialogOptionPacket(
            int survivorId,
            int currentNpcReactionId,
            int playerReactionId,
            int resolvedNpcReactionId
    ) {
        this.survivorId = survivorId;
        this.currentNpcReactionId = currentNpcReactionId;
        this.playerReactionId = playerReactionId;
        this.resolvedNpcReactionId = resolvedNpcReactionId;
    }

    public C2SDialogOptionPacket(FriendlyByteBuf buf) {
        this.survivorId = buf.readInt();
        this.currentNpcReactionId = buf.readInt();
        this.playerReactionId = buf.readInt();
        this.resolvedNpcReactionId = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.survivorId);
        buf.writeInt(this.currentNpcReactionId);
        buf.writeInt(this.playerReactionId);
        buf.writeInt(this.resolvedNpcReactionId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }

            Entity entity = player.level().getEntity(this.survivorId);
            if (!(entity instanceof SurvivorEntity survivor)) {
                return;
            }

            // The client must be acting on the NPC reaction currently shown.
            DialogManager.NpcReaction current =
                    DialogManager.getNpcReaction(this.currentNpcReactionId);
            if (current == null) {
                return;
            }

            if (!current.options().contains(this.playerReactionId)) {
                return;
            }

            DialogManager.NpcReaction expected =
                    DialogManager.resolvePlayerReaction(
                            survivor,
                            this.playerReactionId
                    );

            if (expected == null) {
                return;
            }

            // Never trust the target chosen by the client.
            if (expected.id() != this.resolvedNpcReactionId) {
                return;
            }

            // Trust is coupled to the actual NPC reaction selected by the
            // generated conditions. It is applied exactly once on the server.
            if (expected.trustDelta() != 0) {
                survivor.addTrust(expected.trustDelta());
            }
        });

        ctx.get().setPacketHandled(true);
    }
}
