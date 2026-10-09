package net.kb150.survivorcolonies.network;

import net.kb150.survivorcolonies.SurvivorColonies;
import net.kb150.survivorcolonies.data.DialogManager;
import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> Server: Der Spieler hat eine Antwort gewaehlt.
 * Der Server validiert den Schritt vollstaendig und autoritativ.
 * Weder das Ziel noch das Vertrauens-Delta werden ungeprueft vom Client uebernommen!
 */
public class C2SDialogOptionPacket {
    private final int survivorId;
    private final int currentNpcReactionId;
    private final int playerReactionId;
    private final int expectedTargetNpcId;

    public C2SDialogOptionPacket(
            int survivorId,
            int currentNpcReactionId,
            int playerReactionId,
            int expectedTargetNpcId
    ) {
        this.survivorId = survivorId;
        this.currentNpcReactionId = currentNpcReactionId;
        this.playerReactionId = playerReactionId;
        this.expectedTargetNpcId = expectedTargetNpcId;
    }

    public C2SDialogOptionPacket(FriendlyByteBuf buf) {
        this.survivorId = buf.readInt();
        this.currentNpcReactionId = buf.readInt();
        this.playerReactionId = buf.readInt();
        this.expectedTargetNpcId = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.survivorId);
        buf.writeInt(this.currentNpcReactionId);
        buf.writeInt(this.playerReactionId);
        buf.writeInt(this.expectedTargetNpcId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Entity entity = player.level().getEntity(this.survivorId);
            if (!(entity instanceof SurvivorEntity survivor) || !survivor.isAlive()) return;

            // 1. Pruefen, ob der NPC-Knoten die gewaehlte Spieler-Option ueberhaupt zulaesst
            DialogManager.NpcReaction current = DialogManager.getNpcReaction(this.currentNpcReactionId);
            if (current == null || !current.options().contains(this.playerReactionId)) {
                SurvivorColonies.LOGGER.warn("[SECURITY] Spieler {} sendete unzulaessige Option {} fuer NPC-Knoten {}",
                        player.getName().getString(), this.playerReactionId, this.currentNpcReactionId);
                return;
            }

            // 2. Kante server-autoritativ evaluieren
            DialogManager.EdgeResolution resolution = DialogManager.resolvePlayerReaction(survivor, this.playerReactionId);
            if (resolution == null) {
                SurvivorColonies.LOGGER.error("[DIALOG-ERROR] Kanten-Aufloesung fuer Player-Node {} schlug serverseitig fehl!", this.playerReactionId);
                return;
            }

            // 3. Sicherheitscheck: Stimmt das serverseitige Ziel mit dem vom Client vorhergesagten ueberein?
            if (resolution.npcReaction().id() != this.expectedTargetNpcId) {
                SurvivorColonies.LOGGER.warn("[DESYNC] Client vermutete Folge-NPC {}, Server ermittelte {}",
                        this.expectedTargetNpcId, resolution.npcReaction().id());
            }

            // 4. Server speichert den Dialog-Fortschritt
            survivor.setDialogState(player.getUUID(), resolution.npcReaction().id());

            // 5. Vertrauen wird exakt einmal mit dem Wert der Kante veraendert
            if (resolution.trustDelta() != 0) {
                survivor.addTrust(resolution.trustDelta());
                SurvivorColonies.LOGGER.info("[DIALOG] {} waehlte Option #{} -> Folge-NPC #{}, Trust-Delta: {} (Neuer Trust: {})",
                        player.getName().getString(), this.playerReactionId, resolution.npcReaction().id(),
                        resolution.trustDelta(), survivor.getTrust());
            }
        });

        ctx.get().setPacketHandled(true);
    }
}