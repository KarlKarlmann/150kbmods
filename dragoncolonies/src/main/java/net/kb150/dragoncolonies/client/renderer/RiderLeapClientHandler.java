package net.kb150.dragoncolonies.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.kb150.dragoncolonies.DragonColonies;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Steuert die rein clientseitige Illusion des Drachenreiter-Sprungs.
 * Blendet die echte Wache am Drachensattel kurz aus und projiziert stattdessen
 * eine blickdichte Kopie mit hoher Geschwindigkeit entlang der Flugbahn.
 */
@Mod.EventBusSubscriber(modid = DragonColonies.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RiderLeapClientHandler {

    private record ActiveLeap(
            int citizenId,
            Vec3 startPos,
            Vec3 targetPos,
            int maxTicks,
            int[] elapsedTicks
    ) {
    }

    private static final Map<Integer, ActiveLeap> ACTIVE_LEAPS = new ConcurrentHashMap<>();

    private RiderLeapClientHandler() {}

    public static void startLeap(int citizenId, Vec3 startPos, Vec3 targetPos, int durationTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // Sound-Kombination aus Riptide-Schub und Warden-Schockwelle
        mc.level.playLocalSound(
                startPos.x, startPos.y, startPos.z,
                SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS,
                1.3F, 1.1F, false
        );
        mc.level.playLocalSound(
                startPos.x, startPos.y, startPos.z,
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS,
                0.6F, 1.7F, false
        );

        // Lokale Partikelspur für den Sonic Streak
        Vec3 trajectory = targetPos.subtract(startPos);
        double distance = trajectory.length();
        if (distance > 0.1D) {
            Vec3 step = trajectory.normalize().scale(0.5D);
            Vec3 current = startPos;
            for (double d = 0; d < distance; d += 0.5D) {
                mc.level.addParticle(
                        ParticleTypes.SWEEP_ATTACK,
                        current.x, current.y + 0.5D, current.z,
                        0.0D, 0.05D, 0.0D
                );
                mc.level.addParticle(
                        ParticleTypes.SONIC_BOOM,
                        current.x, current.y + 0.8D, current.z,
                        0.0D, 0.0D, 0.0D
                );
                current = current.add(step);
            }
        }

        ACTIVE_LEAPS.put(citizenId, new ActiveLeap(citizenId, startPos, targetPos, durationTicks, new int[]{0}));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Iterator<Map.Entry<Integer, ActiveLeap>> it = ACTIVE_LEAPS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, ActiveLeap> entry = it.next();
            ActiveLeap leap = entry.getValue();
            leap.elapsedTicks[0]++;
            if (leap.elapsedTicks[0] >= leap.maxTicks) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        // Blendet den echten Bürger am Sattel aus, während das Phantom noch fliegt
        if (ACTIVE_LEAPS.containsKey(event.getEntity().getId())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || ACTIVE_LEAPS.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        float partialTick = event.getPartialTick();
        PoseStack poseStack = event.getPoseStack();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        for (ActiveLeap leap : ACTIVE_LEAPS.values()) {
            Entity entity = mc.level.getEntity(leap.citizenId);
            if (entity == null) continue;

            float progress = (leap.elapsedTicks[0] + partialTick) / (float) leap.maxTicks;
            progress = Mth.clamp(progress, 0.0F, 1.0F);

            Vec3 interpolatedPos = leap.startPos.lerp(leap.targetPos, progress);
            Vec3 dir = leap.targetPos.subtract(leap.startPos);

            poseStack.pushPose();
            poseStack.translate(interpolatedPos.x - camPos.x, interpolatedPos.y - camPos.y, interpolatedPos.z - camPos.z);

            // Ausrichtung in Sprungrichtung mit leichter Hechtneigung
            float yaw = (float) (Mth.atan2(dir.z, dir.x) * (180.0D / Math.PI)) - 90.0F;
            poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(25.0F));

            int packedLight = dispatcher.getPackedLightCoords(entity, partialTick);
            dispatcher.render(entity, 0.0D, 0.0D, 0.0D, yaw, partialTick, poseStack, bufferSource, packedLight);

            poseStack.popPose();
        }

        bufferSource.endBatch();
    }
}