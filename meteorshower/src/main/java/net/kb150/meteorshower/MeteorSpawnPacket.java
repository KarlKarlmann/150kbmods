package net.kb150.meteorshower;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class MeteorSpawnPacket {
    public final double startX, startY, startZ;
    public final double endX, endY, endZ;
    public final int flightDuration;

    public MeteorSpawnPacket(Vec3 start, Vec3 end, int flightDuration) {
        this.startX = start.x;
        this.startY = start.y;
        this.startZ = start.z;
        this.endX = end.x;
        this.endY = end.y;
        this.endZ = end.z;
        this.flightDuration = flightDuration;
    }

    public MeteorSpawnPacket(FriendlyByteBuf buf) {
        this.startX = buf.readDouble();
        this.startY = buf.readDouble();
        this.startZ = buf.readDouble();
        this.endX = buf.readDouble();
        this.endY = buf.readDouble();
        this.endZ = buf.readDouble();
        this.flightDuration = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeDouble(startX);
        buf.writeDouble(startY);
        buf.writeDouble(startZ);
        buf.writeDouble(endX);
        buf.writeDouble(endY);
        buf.writeDouble(endZ);
        buf.writeInt(flightDuration);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        
        if (context.getDirection().getReceptionSide().isClient()) {
            context.enqueueWork(() -> {
                // Hier rufen wir eine Methode auf, die weiter unten steht
                handleOnClient();
            });
        }
        
        return true;
    }

    // Diese Methode wird vom Server-Compiler gnädig ignoriert, solange sie nicht aufgerufen wird.
    // WICHTIG: Nutze hier den vollen Pfad (Fully Qualified Name), 
    // dann sparst du dir den problematischen Import oben!
    private void handleOnClient() {
        Vec3 start = new Vec3(startX, startY, startZ);
        Vec3 end = new Vec3(endX, endY, endZ);
        net.kb150.meteorshower.client.MeteorClientHandler.addMeteor(start, end, flightDuration);
    }
}