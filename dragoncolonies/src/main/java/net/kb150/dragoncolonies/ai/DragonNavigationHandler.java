package net.kb150.dragoncolonies.ai;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.kb150.dragoncolonies.DragonColonies;
import net.kb150.dragoncolonies.util.CitizenReasonResolver;
import net.magister.bookofdragons.entity.ai.movement.AIMovementComponent;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.bookofdragons.entity.state.TransportMode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Bridge zwischen MineColonies und Book of Dragons (BoD).
// Hält den Drachen bei Luftpatrouillen kontinuierlich in Bewegung:
// Bei Delays von MineColonies kreist der Drache mit realem Schub im Loiter-Orbit,
// statt in der asynchronen BoD-Pfadsuche einzufrieren oder auf 0 abzubremsen.
public final class DragonNavigationHandler {

    private static final int MOUNT_DELAY_TICKS = 30;
    private static final double GROUND_MOUNT_DISTANCE = 8.0D;
    private static final int MAX_NO_PROGRESS_TICKS = 20 * 5;
    private static final double MIN_PROGRESS_DISTANCE = 0.20D;

    // Fly-Through Radius für Luftziele: Minecolonies erfährt frühzeitig von Ankunft
    private static final double AIR_PASS_BY_DISTANCE_SQ = 7.0D * 7.0D;
    private static final double LOITER_RADIUS = 20.0D;

    private static final Map<UUID, BlockPos> GROUND_TASK_TARGET = new HashMap<>();
    private static final Map<UUID, Integer> MOUNT_DELAY = new HashMap<>();
    private static final Map<UUID, BlockPos> MOUNT_DELAY_TARGET = new HashMap<>();
    private static final Map<UUID, ActiveTransport> ACTIVE = new HashMap<>();
    private static final Map<UUID, BlockPos> AIR_TARGET_ARRIVED = new HashMap<>();

    // Speichert aktive Loiter-Zentren für flüssiges Kreisen bei Pausen
    private static final Map<UUID, LoiterState> ACTIVE_LOITERS = new HashMap<>();

    private record ActiveTransport(
            BlockPos target,
            Vec3 dragonTarget,
            boolean airTarget,
            Vec3 lastPosition,
            int noProgressTicks,
            int groundHandoffTicks
    ) {}

    private static class LoiterState {
        final BlockPos center;
        double currentAngle;
        final double speed;

        LoiterState(BlockPos center, double startAngle, double speed) {
            this.center = center;
            this.currentAngle = startAngle;
            this.speed = speed;
        }
    }

    private DragonNavigationHandler() {}

    public static Boolean handleDragonFlight(
            AbstractEntityCitizen citizen,
            BlockPos target,
            int distToDesired
    ) {
        if (citizen == null || target == null || !(citizen.level() instanceof ServerLevel level)) {
            return null;
        }

        AbstractEntityAIDragonRider<?, ?> rider =
                AbstractEntityAIDragonRider.getRiderForCitizen(citizen);
        DragonBase dragon = rider == null ? null : rider.getAssignedDragon();

        if (rider == null || dragon == null || !dragon.isAlive()) {
            return null;
        }

        UUID id = citizen.getUUID();
        boolean riding = citizen.getVehicle() == dragon;
        boolean airTarget = isAirTarget(level, target);

        BlockPos groundTarget = GROUND_TASK_TARGET.get(id);
        if (groundTarget != null && !groundTarget.equals(target)) {
            GROUND_TASK_TARGET.remove(id);
            groundTarget = null;
        }

        if (riding && citizen.getTarget() != null && citizen.getTarget().isAlive()) {
            clearTransport(id, dragon);
            ACTIVE_LOITERS.remove(id);
            return false;
        }

        ActiveTransport active = ACTIVE.get(id);
        if (active != null) {
            // Ignoriere minimale Wegpunktkorrekturen im Nahbereich, um Neuberechnungen zu verhindern
            if (!active.target().equals(target)) {
                if (active.target().closerThan(target, 4.0D)) {
                    return false;
                }
                clearTransport(id, dragon);
                active = null;
            }
        }

        // Wenn ein Luftziel gerade als erreicht markiert wurde: MineColonies quittieren
        BlockPos arrivedAir = AIR_TARGET_ARRIVED.get(id);
        if (airTarget && target.equals(arrivedAir)) {
            AIR_TARGET_ARRIVED.remove(id);
            return true;
        }

        // Neues Ziel erhalten: Alten Loiter-Zustand sofort beenden
        if (active != null && !active.target().equals(target)) {
            ACTIVE_LOITERS.remove(id);
        }

        if (!riding) {
            if (target.equals(groundTarget)) {
                MOUNT_DELAY.remove(id);
                MOUNT_DELAY_TARGET.remove(id);
                return null;
            }

            double distanceToTarget = citizen.distanceToSqr(
                    target.getX() + 0.5D,
                    target.getY(),
                    target.getZ() + 0.5D
            );

            if (!airTarget && distanceToTarget <= GROUND_MOUNT_DISTANCE * GROUND_MOUNT_DISTANCE) {
                MOUNT_DELAY.remove(id);
                MOUNT_DELAY_TARGET.remove(id);
                return null;
            }

            BlockPos delayedTarget = MOUNT_DELAY_TARGET.get(id);
            if (delayedTarget == null || !delayedTarget.equals(target)) {
                MOUNT_DELAY_TARGET.put(id, target);
                MOUNT_DELAY.put(id, MOUNT_DELAY_TICKS);
            }

            return null;
        }

        MOUNT_DELAY.remove(id);
        MOUNT_DELAY_TARGET.remove(id);

        citizen.getNavigation().stop();
        if (active == null) {
            // Neues Ziel eingetroffen: Loiter beenden
            ACTIVE_LOITERS.remove(id);

            Vec3 dragonTarget = toDragonTarget(target);
            AIMovementComponent movement = dragon.getAIMovement();
            if (movement == null) {
                return null;
            }

            BoDPathInfo.clear(dragon);
            alignDragonToTarget(dragon, dragonTarget);
            movement.setRecalculationInterval(120);

            ActiveTransport newActive = new ActiveTransport(
                    target,
                    dragonTarget,
                    airTarget,
                    dragon.position(),
                    0,
                    -1
            );
            ACTIVE.put(id, newActive);

            CitizenReasonResolver.ReasonInfo reason = CitizenReasonResolver.resolveReason(citizen);
            DragonColonies.debug("NAVIGATION", "Flugauftrag gestartet | Buerger: {} | Ziel: {} | Air: {} | Grund: {}",
                    citizen.getName().getString(), target.toShortString(), airTarget, reason);

            movement.setWaypoint(dragonTarget, 1.0D, arrivedDragon -> {
                ActiveTransport current = ACTIVE.get(id);
                if (current == null || !current.target().equals(target)) {
                    return;
                }

                CitizenReasonResolver.ReasonInfo arrivalReason = CitizenReasonResolver.resolveReason(citizen);

                if (current.airTarget()) {
                    DragonColonies.debug("NAVIGATION", "Luftziel erreicht (Callback) | Buerger: {} | Ziel: {} | Grund: {}",
                            citizen.getName().getString(), current.target().toShortString(), arrivalReason);
                    ACTIVE.remove(id);
                    AIR_TARGET_ARRIVED.put(id, current.target());
                    startLoitering(id, arrivedDragon, current.target());
                    return;
                }

                DragonColonies.debug("NAVIGATION", "Bodenziel erreicht | Buerger: {} | Ziel: {} | Grund: {}",
                        citizen.getName().getString(), current.target().toShortString(), arrivalReason);
                ACTIVE.remove(id);
                MOUNT_DELAY.remove(id);
                MOUNT_DELAY_TARGET.remove(id);
                BoDPathInfo.clear(arrivedDragon);
                dismount(arrivedDragon, citizen);
            });

            return false;
        }

        return false;
    }

    private static void startLoitering(UUID citizenId, DragonBase dragon, BlockPos center) {
        Vec3 dragonPos = dragon.position();
        double dx = dragonPos.x - (center.getX() + 0.5D);
        double dz = dragonPos.z - (center.getZ() + 0.5D);
        double initialAngle = Math.atan2(dz, dx);

        // Reisegeschwindigkeit aus den Stats ermitteln (Fallback 0.55 Blöcke/Tick)
        double speed = 0.55D;
        if (dragon.getStatSheet() != null) {
            speed = Math.max(0.40D, dragon.getStatSheet().airMaxVelocity * 0.85D);
        }

        ACTIVE_LOITERS.put(citizenId, new LoiterState(center, initialAngle, speed));
    }

    public static void serverTick(MinecraftServer server) {
        for (Map.Entry<UUID, Integer> entry : new ArrayList<>(MOUNT_DELAY.entrySet())) {
            UUID id = entry.getKey();
            AbstractEntityCitizen citizen = findCitizen(server, id);
            if (citizen == null || citizen.isPassenger()) {
                MOUNT_DELAY.remove(id);
                MOUNT_DELAY_TARGET.remove(id);
                continue;
            }

            int value = entry.getValue() - 1;
            if (value <= 0) {
                MOUNT_DELAY.remove(id);
                MOUNT_DELAY_TARGET.remove(id);
                forceMountForCitizen(server, id);
            } else {
                entry.setValue(value);
            }
        }

        // Autonomer physikalischer Loiter-Orbit während MineColonies-Delays
        for (Map.Entry<UUID, LoiterState> entry : new ArrayList<>(ACTIVE_LOITERS.entrySet())) {
            UUID id = entry.getKey();
            LoiterState loiter = entry.getValue();

            // Falls MineColonies bereits ein neues aktives Ziel geschickt hat, Orbit beenden
            if (ACTIVE.containsKey(id)) {
                ACTIVE_LOITERS.remove(id);
                continue;
            }

            AbstractEntityCitizen citizen = findCitizen(server, id);
            if (citizen == null || !citizen.isPassenger()) {
                ACTIVE_LOITERS.remove(id);
                continue;
            }

            AbstractEntityAIDragonRider<?, ?> rider = AbstractEntityAIDragonRider.getRiderForCitizen(citizen);
            DragonBase dragon = rider == null ? null : rider.getAssignedDragon();

            if (dragon == null || !dragon.isAlive() || citizen.getVehicle() != dragon) {
                ACTIVE_LOITERS.remove(id);
                continue;
            }

            // Physikalischen Flugvektor tangential entlang der Kreisbahn berechnen
            // dTheta = arc_length / radius
            double dTheta = loiter.speed / LOITER_RADIUS;
            loiter.currentAngle += dTheta;

            // Punkt auf dem Kreisumfang
            double targetX = (loiter.center.getX() + 0.5D) + Math.cos(loiter.currentAngle) * LOITER_RADIUS;
            double targetZ = (loiter.center.getZ() + 0.5D) + Math.sin(loiter.currentAngle) * LOITER_RADIUS;
            double targetY = loiter.center.getY();

            Vec3 desiredPos = new Vec3(targetX, targetY, targetZ);
            Vec3 toTangent = desiredPos.subtract(dragon.position());

            // Vektor normalisieren und auf Air-Speed skalieren
            if (toTangent.lengthSqr() > 0.01D) {
                Vec3 velocity = toTangent.normalize().scale(loiter.speed);
                dragon.setDeltaMovement(velocity);

                // Drachen-Ausrichtung kontinuierlich in Flugrichtung ziehen
                float targetYaw = (float) (-Mth.atan2(velocity.x, velocity.z) * (180.0D / Math.PI));
                dragon.setYRot(targetYaw);
                dragon.yBodyRot = targetYaw;
                dragon.yHeadRot = targetYaw;
            }
        }

        for (Map.Entry<UUID, ActiveTransport> entry : new ArrayList<>(ACTIVE.entrySet())) {
            UUID id = entry.getKey();
            ActiveTransport active = entry.getValue();

            AbstractEntityCitizen citizen = findCitizen(server, id);
            if (citizen == null) {
                ACTIVE.remove(id);
                continue;
            }

            AbstractEntityAIDragonRider<?, ?> rider =
                    AbstractEntityAIDragonRider.getRiderForCitizen(citizen);
            DragonBase dragon = rider == null ? null : rider.getAssignedDragon();

            if (dragon == null || !dragon.isAlive() || citizen.getVehicle() != dragon) {
                continue;
            }

            AIMovementComponent movement = dragon.getAIMovement();
            if (movement == null) {
                continue;
            }

            Vec3 currentPosition = dragon.position();
            double movedDistance = currentPosition.distanceTo(active.lastPosition());

            int noProgressTicks = movedDistance < MIN_PROGRESS_DISTANCE
                    ? active.noProgressTicks() + 1
                    : 0;

            ActiveTransport updatedActive = new ActiveTransport(
                    active.target(),
                    active.dragonTarget(),
                    active.airTarget(),
                    movedDistance >= MIN_PROGRESS_DISTANCE ? currentPosition : active.lastPosition(),
                    noProgressTicks,
                    active.groundHandoffTicks()
            );

            ACTIVE.put(id, updatedActive);
            active = updatedActive;

            if (dragon.getTransportMode() == TransportMode.AIRBORNE) {
                dragon.yBodyRot = dragon.getYRot();
                dragon.yHeadRot = dragon.getYRot();
            }

            // Fly-Through Erkennung: Übergang in den Loiter-Orbit bereits im Vorbeiflug
            if (active.airTarget()) {
                double distSqToAirTarget = dragon.distanceToSqr(active.dragonTarget());
                if (distSqToAirTarget <= AIR_PASS_BY_DISTANCE_SQ) {
                    CitizenReasonResolver.ReasonInfo arrivalReason = CitizenReasonResolver.resolveReason(citizen);
                    DragonColonies.debug("NAVIGATION", "Luftziel erreicht (Fly-Through) | Buerger: {} | Ziel: {} | Dist: {}m",
                            citizen.getName().getString(), active.target().toShortString(), String.format("%.1f", Math.sqrt(distSqToAirTarget)));

                    ACTIVE.remove(id);
                    AIR_TARGET_ARRIVED.put(id, active.target());
                    startLoitering(id, dragon, active.target());
                    continue;
                }
            }

            if (active.noProgressTicks() >= MAX_NO_PROGRESS_TICKS) {
                DragonColonies.debug("NAVIGATION", "Unstuck Watchdog ausgeloest fuer {} -> Teleport zu {}",
                        citizen.getName().getString(), active.target().toShortString());
                teleportDragonAndRiderToTarget(citizen, dragon, active.target());
                ACTIVE.remove(id);
                BoDPathInfo.clear(dragon);
                continue;
            }

            if (movement.hasFailed()) {
                DragonColonies.debug("NAVIGATION", "BoD meldet Navigationsfehlschlag fuer {}",
                        active.target().toShortString());
                ACTIVE.remove(id);
                BoDPathInfo.clear(dragon);
                continue;
            }

            double distanceToGroundTarget = dragon.distanceToSqr(active.dragonTarget());

            if (!active.airTarget()) {
                int groundHandoffTicks = active.groundHandoffTicks();

                if (groundHandoffTicks < 0
                        && distanceToGroundTarget <= GROUND_MOUNT_DISTANCE * GROUND_MOUNT_DISTANCE) {
                    groundHandoffTicks = MOUNT_DELAY_TICKS;
                }

                if (groundHandoffTicks >= 0) {
                    groundHandoffTicks--;
                    if (groundHandoffTicks <= 0) {
                        finishGroundAtCurrentPosition(id, active.target(), citizen, dragon);
                        continue;
                    }
                }

                active = new ActiveTransport(
                        active.target(),
                        active.dragonTarget(),
                        active.airTarget(),
                        active.lastPosition(),
                        active.noProgressTicks(),
                        groundHandoffTicks
                );
                ACTIVE.put(id, active);
            }
        }
    }

    public static void alignDragonToTarget(DragonBase dragon, Vec3 targetPos) {
        Vec3 diff = targetPos.subtract(dragon.position());
        double horizDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        if (horizDist > 0.1D) {
            float targetYaw = (float) (-Mth.atan2(diff.x, diff.z) * (180.0D / Math.PI));
            dragon.setYRot(targetYaw);
            dragon.yRotO = targetYaw;
            dragon.yBodyRot = targetYaw;
            dragon.yHeadRot = targetYaw;
        }
    }

    public static BlockPos getHighAirPos(ServerLevel level, BlockPos pos) {
        int surfaceY = level.getHeightmapPos(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(pos.getX(), 0, pos.getZ())
        ).getY();

        return new BlockPos(
                pos.getX(),
                Math.max(pos.getY() + 12, surfaceY + 12),
                pos.getZ()
        );
    }

    private static boolean isAirTarget(ServerLevel level, BlockPos target) {
        return level.getBlockState(target.below()).isAir() &&
               level.getBlockState(target.below(2)).isAir();
    }

    private static Vec3 toDragonTarget(BlockPos target) {
        return new Vec3(
                target.getX() + 0.5D,
                target.getY(),
                target.getZ() + 0.5D
        );
    }

    private static void forceMountForCitizen(MinecraftServer server, UUID id) {
        AbstractEntityCitizen citizen = findCitizen(server, id);
        if (citizen == null || citizen.isPassenger()) {
            return;
        }

        AbstractEntityAIDragonRider<?, ?> rider =
                AbstractEntityAIDragonRider.getRiderForCitizen(citizen);
        DragonBase dragon = rider == null ? null : rider.getAssignedDragon();
        if (rider != null && dragon != null && dragon.isAlive()) {
            forceMount(citizen, dragon, rider);
        }
    }

    private static AbstractEntityCitizen findCitizen(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            var entity = level.getEntity(id);
            if (entity instanceof AbstractEntityCitizen citizen) {
                return citizen;
            }
        }
        return null;
    }

    private static void finishGroundAtCurrentPosition(
            UUID id,
            BlockPos target,
            AbstractEntityCitizen citizen,
            DragonBase dragon
    ) {
        CitizenReasonResolver.ReasonInfo reason = CitizenReasonResolver.resolveReason(citizen);
        DragonColonies.debug("NAVIGATION", "Boden-Handoff abgeschlossen | Buerger: {} | Ziel: {} | Grund: {}",
                citizen.getName().getString(), target.toShortString(), reason);

        ACTIVE.remove(id);
        ACTIVE_LOITERS.remove(id);
        GROUND_TASK_TARGET.put(id, target);
        MOUNT_DELAY.remove(id);
        MOUNT_DELAY_TARGET.remove(id);
        dismount(dragon, citizen);
    }

    private static void teleportDragonAndRiderToTarget(
            AbstractEntityCitizen citizen,
            DragonBase dragon,
            BlockPos target
    ) {
        double x = target.getX() + 0.5D;
        double y = target.getY();
        double z = target.getZ() + 0.5D;

        dragon.teleportTo(x, y, z);
        citizen.teleportTo(x, y, z);
    }

    private static void clearTransport(UUID id, DragonBase dragon) {
        ACTIVE.remove(id);
        BoDPathInfo.clear(dragon);
    }

    private static void forceMount(
            AbstractEntityCitizen citizen,
            DragonBase dragon,
            AbstractEntityAIDragonRider<?, ?> rider
    ) {
        if (citizen.getVehicle() == dragon) {
            return;
        }
        rider.handleMountingPhase(citizen);
    }

    private static void dismount(DragonBase dragon, AbstractEntityCitizen citizen) {
        if (citizen.getVehicle() == dragon) {
            citizen.stopRiding();
        }

        AIMovementComponent movement = dragon.getAIMovement();
        if (movement != null) {
            movement.clearAllWaypoints();
        }

        dragon.setTransportMode(TransportMode.GROUNDED);
    }
}