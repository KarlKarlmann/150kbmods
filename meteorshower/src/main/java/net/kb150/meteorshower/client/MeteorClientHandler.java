package net.kb150.meteorshower.client;

import net.kb150.meteorshower.MeteorShower;
import net.kb150.meteorshower.MeteorSpawnPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// Diese Klasse darf NUR auf dem Client geladen werden!
@Mod.EventBusSubscriber(modid = MeteorShower.MOD_ID, value = Dist.CLIENT)
public class MeteorClientHandler {

    // Liste aller aktuell fliegenden Meteore
    private static final List<ClientMeteor> activeMeteors = new ArrayList<>();

    // Wird vom MeteorSpawnPacket aufgerufen
    public static void addMeteor(Vec3 start, Vec3 end, int duration) {
        activeMeteors.add(new ClientMeteor(start, end, duration));
    }

    // Holt uns alle Meteore (wird vom Renderer gebraucht)
    public static List<ClientMeteor> getActiveMeteors() {
        return activeMeteors;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // Ticke alle Meteore runter und lösche sie, wenn sie den Boden berühren
        Iterator<ClientMeteor> iterator = activeMeteors.iterator();
        while (iterator.hasNext()) {
            ClientMeteor meteor = iterator.next();
            meteor.currentTicks++;
            
            if (meteor.currentTicks >= meteor.totalTicks) {
                iterator.remove();
            }
        }
    }

    // --- Innere Klasse für einen einzelnen sichtbaren Meteor ---
    public static class ClientMeteor {
        public final Vec3 startPos;
        public final Vec3 endPos;
        public final int totalTicks;
        public int currentTicks = 0;

        public ClientMeteor(Vec3 startPos, Vec3 endPos, int totalTicks) {
            this.startPos = startPos;
            this.endPos = endPos;
            this.totalTicks = totalTicks;
        }

        /**
         * Berechnet die butterweiche Position für den Renderer
         */
        public Vec3 getInterpolatedPosition(float partialTick) {
            float exactTick = this.currentTicks + partialTick;
            float progress = Math.min(exactTick / (float) this.totalTicks, 1.0f);

            // Optional: Easing (Quadratisch), damit der Meteor nach unten beschleunigt
            progress = progress * progress;

            double currentX = Mth.lerp(progress, startPos.x, endPos.x);
            double currentY = Mth.lerp(progress, startPos.y, endPos.y);
            double currentZ = Mth.lerp(progress, startPos.z, endPos.z);

            return new Vec3(currentX, currentY, currentZ);
        }
    }
}