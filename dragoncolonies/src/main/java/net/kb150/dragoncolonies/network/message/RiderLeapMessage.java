package net.kb150.dragoncolonies.network.message;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server-to-Client Ereignispaket für den Failsafe-Snap des Drachenreiters.
 * Überträgt nur die nötigsten Raumdaten, damit der Client Sound, Partikel
 * und das Dash-Modell butterweich lokal berechnen kann.
 */
public class RiderLeapMessage {

    private final int citizenEntityId;
    private final Vec3 startPos;
    private final Vec3 targetPos;
    private final int durationTicks;

    public RiderLeapMessage(int citizenEntityId, Vec3 startPos, Vec3 targetPos, int durationTicks) {
        this.citizenEntityId = citizenEntityId;
        this.startPos = startPos;
        this.targetPos = targetPos;
        this.durationTicks = durationTicks;
    }

    public static void encode(RiderLeapMessage msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.citizenEntityId);
        buf.writeDouble(msg.startPos.x);
        buf.writeDouble(msg.startPos.y);
        buf.writeDouble(msg.startPos.z);
        buf.writeDouble(msg.targetPos.x);
        buf.writeDouble(msg.targetPos.y);
        buf.writeDouble(msg.targetPos.z);
        buf.writeInt(msg.durationTicks);
    }

    public static RiderLeapMessage decode(FriendlyByteBuf buf) {
        return new RiderLeapMessage(
                buf.readInt(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readInt()
        );
    }

    public static void handle(RiderLeapMessage msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            // Verhindert Classloading-Abstürze auf dem Dedicated Server
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(msg));
        });
        ctx.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(RiderLeapMessage msg) {
        net.kb150.dragoncolonies.client.render.RiderLeapClientHandler.startLeap(
                msg.citizenEntityId,
                msg.startPos,
                msg.targetPos,
                msg.durationTicks
        );
    }
}