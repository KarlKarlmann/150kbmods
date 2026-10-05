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
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bridge zwischen MineColonies und Book of Dragons (BoD).
 *
 * MineColonies liefert das Navigationsziel und die semantischen Gruende.
 * BoD uebernimmt die dreidimensionale Flugnavigation.
 * Wir steuern, wann der Reiter abgesetzt oder an MineColonies uebergeben wird.
 */
public final class DragonNavigationHandler {

    private static final int MOUNT_DELAY_TICKS = 30;
    private static final double GROUND_MOUNT_DISTANCE = 8.0D;
    private static final int MAX_NO_PROGRESS_TICKS = 20 * 5;
    private static final double MIN_PROGRESS_DISTANCE = 0.20D;

    private static final Map<UUID, BlockPos> GROUND_TASK_TARGET = new HashMap<>();
    private static final Map<UUID, Integer> MOUNT_DELAY = new HashMap<>();
    private static final Map<UUID, BlockPos> MOUNT_DELAY_TARGET = new HashMap<>();
    private static final Map<UUID, ActiveTransport> ACTIVE = new HashMap<>();
    private static final Map<UUID, BlockPos> AIR_TARGET_ARRIVED = new HashMap<>();

    private record ActiveTransport(
            BlockPos target,
            Vec3 dragonTarget,
            boolean airTarget,
            Vec3 lastPosition,
            int noProgressTicks,
            int groundHandoffTicks
    ) {
    }

    private DragonNavigationHandler() {}

    /**
     * Steuert den Drachentransport fuer MineColonies-Buerger.
     *
     * @param citizen Der steuernde Buerger
     * @param target Das angestrebte Ziel
     * @param distToDesired Zulaessige Naeherungsdistanz
     * @return null = MineColonies darf Vanilla weiterlaufen;
     *         false = DragonColonies/BoD bearbeitet den Transport;
     *         true = MineColonies-Target gilt als erreicht.
     */
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

        // 1. ZIELWECHSEL-PRUEFUNG: Wenn Minecolonies ein neues Ziel vorgibt, altes Ground-Target loeschen
        BlockPos groundTarget = GROUND_TASK_TARGET.get(id);
        if (groundTarget != null && !groundTarget.equals(target)) {
            GROUND_TASK_TARGET.remove(id);
            groundTarget = null;
        }

        // Combat bleibt beim vorhandenen Combat-System.
        if (riding && citizen.getTarget() != null && citizen.getTarget().isAlive()) {
            clearTransport(id, dragon);
            return false;
        }

        // Ein alter Transportauftrag ist vorbei, sobald Minecolonies ein neues Ziel vorgibt.
        ActiveTransport active = ACTIVE.get(id);
        if (active != null && !active.target().equals(target)) {
            clearTransport(id, dragon);
            active = null;
        }

        // Ein erreichtes Luftziel muss einmal an Minecolonies zurueckgemeldet werden.
        BlockPos arrivedAir = AIR_TARGET_ARRIVED.get(id);
        if (airTarget && target.equals(arrivedAir)) {
            AIR_TARGET_ARRIVED.remove(id);
            return true;
        }

        // Rider ist noch zu Fuss: erst etwas Zeit zum Herauslaufen geben.
        if (!riding) {
            // 2. HANDOFF-CHECK: Wenn wir fuer dieses Ziel schon abgesessen sind -> NIEMALS neu aufsteigen!
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

            // Ist ein Ground-Target schon nah genug, soll Minecolonies einfach laufen.
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
            Vec3 dragonTarget = toDragonTarget(target);
            AIMovementComponent movement = dragon.getAIMovement();
            if (movement == null) {
                return null;
            }

            BoDPathInfo.clear(dragon);

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
            DragonColonies.debug("NAVIGATION", "Flugauftrag initiiert | Buerger: {} | Ziel: {} | Air: {} | Grund: {}",
                    citizen.getName().getString(), target.toShortString(), airTarget, reason);

            movement.setWaypoint(dragonTarget, 1.0D, arrivedDragon -> {
                ActiveTransport current = ACTIVE.get(id);
                if (current == null || !current.target().equals(target)) {
                    return;
                }

                CitizenReasonResolver.ReasonInfo arrivalReason = CitizenReasonResolver.resolveReason(citizen);

                if (current.airTarget()) {
                    DragonColonies.debug("NAVIGATION", "Luftziel erreicht | Buerger: {} | Ziel: {} | Grund: {}",
                            citizen.getName().getString(), current.target().toShortString(), arrivalReason);
                    ACTIVE.remove(id);
                    AIR_TARGET_ARRIVED.put(id, current.target());
                    BoDPathInfo.clear(arrivedDragon);
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

            if (active.noProgressTicks() >= MAX_NO_PROGRESS_TICKS) {
                DragonColonies.debug("NAVIGATION", "Unstuck Watchdog triggered fuer {} -> Teleport zu {}",
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

            BoDPathInfo.Result result = BoDPathInfo.get(dragon);
            if (result == null || result.target() == null || !result.target().equals(active.dragonTarget())) {
                continue;
            }

            if (result.endPoint() == null || result.nodeCount() == 0) {
                if (!active.airTarget()) {
                    finishGroundAtCurrentPosition(id, active.target(), citizen, dragon);
                }
            }
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