package net.kb150.dragoncolonies.ai;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.magister.bookofdragons.entity.ai.movement.AIMovementComponent;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.bookofdragons.entity.state.TransportMode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.kb150.dragoncolonies.DragonColonies;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bridge zwischen Minecolonies und Book of Dragons.
 *
 * Minecolonies liefert das Ziel.
 * BoD uebernimmt die Navigation.
 * Wir entscheiden nur, wann der Rider wieder an Minecolonies uebergeben wird.
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
            int noProgressTicks
    ) {
    }

    private DragonNavigationHandler() {
    }

    /**
     * null  = Minecolonies darf vanilla weiterlaufen.
     * false = DragonColonies/BoD bearbeitet den Transport.
     * true  = Minecolonies-Target gilt als erreicht.
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

        // Combat bleibt beim vorhandenen Combat-System.
        if (riding && citizen.getTarget() != null && citizen.getTarget().isAlive()) {
            clearTransport(id, dragon);
            return false;
        }

        // Derselbe Ground-Auftrag wurde bereits an Minecolonies uebergeben.
        if (!airTarget && target.equals(GROUND_TASK_TARGET.get(id))) {
            return null;
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
        // Das gilt fuer Ground- und Air-Targets gleichermassen.
        if (!riding) {
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
                    0
            );
            ACTIVE.put(id, newActive);

			movement.setWaypoint(dragonTarget, 1.0D, arrivedDragon -> {
                DragonColonies.LOGGER.info("[DRAGON-NAV-DEBUG] Callback von BoD gefeuert! Drache ist am Ziel.");

                ActiveTransport current = ACTIVE.get(id);
                if (current == null || !current.target().equals(target)) {
                    DragonColonies.LOGGER.warn(
                            "[DRAGON-NAV-DEBUG] Callback ignoriert: kein passender aktiver Transport mehr fuer {}.",
                            target.toShortString()
                    );
                    return;
                }

                if (current.airTarget()) {
                    DragonColonies.LOGGER.info("[DRAGON-NAV-DEBUG] Luftziel erreicht. Melde an Minecolonies.");
                    ACTIVE.remove(id);
                    AIR_TARGET_ARRIVED.put(id, current.target());
                    BoDPathInfo.clear(arrivedDragon);
                    return;
                }

                DragonColonies.LOGGER.info("[DRAGON-NAV-DEBUG] Bodenziel erreicht. Rufe dismount() auf.");
                ACTIVE.remove(id);
                GROUND_TASK_TARGET.put(id, current.target());
                MOUNT_DELAY.remove(id);
                MOUNT_DELAY_TARGET.remove(id);
                BoDPathInfo.clear(arrivedDragon);
                dismount(arrivedDragon, citizen);
            });

            return false;
        }

        return false;
    }

    /**
     * Reiner Bridge-Tick fuer Mount-Delay, Nahbereichs-Handoff und Unstuck-Watchdog.
     */
	 
	 
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

            // ---> HIER KOMMT DER DEBUG-BLOCK HIN <---
            if (dragon.tickCount % 20 == 0) {
                DragonColonies.LOGGER.info("[DRAGON-NAV-DEBUG] TICK | Distanz zum Ziel: {} | BoD State: {} | BoD Current Waypoint: {}", 
                        dragon.distanceToSqr(active.dragonTarget()), 
                        movement.getState(), 
                        movement.getCurrentWaypoint() != null ? "Gesetzt" : "NULL");
            }
            // ----------------------------------------

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
                    noProgressTicks
            );
            ACTIVE.put(id, updatedActive);
            active = updatedActive;

            if (active.noProgressTicks() >= MAX_NO_PROGRESS_TICKS) {
                DragonColonies.LOGGER.warn(
                        "[DRAGON-UNSTUCK] Dragon + Rider stecken fest. Teleport zu {}.",
                        active.target().toShortString()
                );
                teleportDragonAndRiderToTarget(citizen, dragon, active.target());
                ACTIVE.remove(id);
                BoDPathInfo.clear(dragon);
                continue;
            }

            if (movement.hasFailed()) {
                DragonColonies.LOGGER.warn(
                        "[DRAGON-NAV] BoD meldet FAILED fuer {}. Transport-State wird verworfen.",
                        active.target().toShortString()
                );
                ACTIVE.remove(id);
                BoDPathInfo.clear(dragon);
                continue;
            }

            double distanceToGroundTarget = dragon.distanceToSqr(active.dragonTarget());
            if (!active.airTarget()
                    && distanceToGroundTarget <= GROUND_MOUNT_DISTANCE * GROUND_MOUNT_DISTANCE) {
                finishGroundAtCurrentPosition(id, active.target(), citizen, dragon);
                continue;
            }

            BoDPathInfo.Result result = BoDPathInfo.get(dragon);
            if (result == null || result.target() == null) {
                continue;
            }

            if (!result.target().equals(active.dragonTarget())) {
                continue;
            }

            if (result.endPoint() == null || result.nodeCount() == 0) {
                if (!active.airTarget()) {
                    finishGroundAtCurrentPosition(id, active.target(), citizen, dragon);
                }
                continue;
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
		// Echte Luftziele (unsere Patrouillen) schweben frei.
		// Ein Minecolonies-Bodenziel (auch im 2. Stock) hat einen Block direkt unter sich.
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
        if (citizen.getVehicle() != dragon) {
            citizen.startRiding(dragon, true);
        }
    }

	private static void dismount(DragonBase dragon, AbstractEntityCitizen citizen) {
		DragonColonies.LOGGER.info("[DRAGON-NAV-DEBUG] DISMOUNT gestartet für: {}", citizen.getName().getString());
		DragonColonies.LOGGER.info("[DRAGON-NAV-DEBUG] Vorher - isPassenger: {}, Vehicle: {}", 
				citizen.isPassenger(), 
				citizen.getVehicle() != null ? citizen.getVehicle().getType().toString() : "NULL");

		if (citizen.getVehicle() == dragon) {
			citizen.stopRiding();
		}

		DragonColonies.LOGGER.info("[DRAGON-NAV-DEBUG] Nachher - isPassenger: {}", citizen.isPassenger());

		AIMovementComponent movement = dragon.getAIMovement();
		if (movement != null) {
			movement.clearAllWaypoints();
		}

		dragon.setTransportMode(TransportMode.GROUNDED);
	}
}
