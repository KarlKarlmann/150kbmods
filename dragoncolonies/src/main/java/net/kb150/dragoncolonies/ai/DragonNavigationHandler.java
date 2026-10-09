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
import java.util.concurrent.ConcurrentHashMap;

public final class DragonNavigationHandler {

    private static final int MOUNT_DELAY_TICKS = 30;
    private static final double GROUND_MOUNT_DISTANCE = 8.0D;
    private static final int MAX_NO_PROGRESS_TICKS = 20 * 5;
    private static final double MIN_PROGRESS_DISTANCE = 0.20D;

    private static final double AIR_PASS_BY_DISTANCE_SQ = 7.0D * 7.0D;
    private static final double LOITER_RADIUS = 20.0D;

    private static final Map<UUID, BlockPos> GROUND_TASK_TARGET = new HashMap<>();
    private static final Map<UUID, Integer> MOUNT_DELAY = new HashMap<>();
    private static final Map<UUID, BlockPos> MOUNT_DELAY_TARGET = new HashMap<>();
    private static final Map<UUID, ActiveTransport> ACTIVE = new HashMap<>();
    private static final Map<UUID, BlockPos> AIR_TARGET_ARRIVED = new HashMap<>();
    private static final Map<UUID, LoiterState> ACTIVE_LOITERS = new HashMap<>();

    // Liste verifizierter Flugziele zur Vermeidung wiederholter Heightmap-Scans auf Dedicated Servern
    private static final Map<BlockPos, BlockPos> SAFE_AIR_CACHE = new ConcurrentHashMap<>();

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
            if (!active.target().equals(target)) {
                if (active.target().closerThan(target, 4.0D)) {
                    return false;
                }
                clearTransport(id, dragon);
                active = null;
            }
        }

        BlockPos arrivedAir = AIR_TARGET_ARRIVED.get(id);
        if (airTarget && target.equals(arrivedAir)) {
            AIR_TARGET_ARRIVED.remove(id);
            return true;
        }

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
            ACTIVE_LOITERS.remove(id);

            Vec3 dragonTarget = toDragonTarget(target);
            AIMovementComponent movement = dragon.getAIMovement();
            if (movement == null) {
                return null;
            }

			BoDPathInfo.clear(dragon);
			alignDragonToTarget(dragon, dragonTarget);
			movement.setRecalculationInterval(120);

			// Bricht BoDs Trägheits-Overshoot bei Kurven >60° ab, damit sich smoothedVelocity nicht in Resonanz aufschaukelt.
			Vec3 currentVel = dragon.getDeltaMovement();
			Vec3 toNewTarget = dragonTarget.subtract(dragon.position()).normalize();
			if (currentVel.lengthSqr() > 0.04D && currentVel.normalize().dot(toNewTarget) < 0.5D) {
				DragonColonies.debug("NAVIGATION", "[DAMPEN] Dämpfe Trägheitsvektor für {} | Dot: {}", 
						citizen.getName().getString(), currentVel.normalize().dot(toNewTarget));
				dragon.setDeltaMovement(currentVel.scale(0.2D));
			}

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

                // Verhindert unberechtigtes Absitzen während Patrouillenflügen selbst bei verfälschten Ground-Flags
                boolean isGroundTask = !rider.hasAxe() || rider.isReturningDragon();

                if (current.airTarget() || !isGroundTask) {
                    DragonColonies.debug("NAVIGATION", "Luftziel/Patrouille erreicht (Callback) | Buerger: {} | Ziel: {} | Grund: {}",
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

        for (Map.Entry<UUID, LoiterState> entry : new ArrayList<>(ACTIVE_LOITERS.entrySet())) {
            UUID id = entry.getKey();
            LoiterState loiter = entry.getValue();

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

            double dTheta = loiter.speed / LOITER_RADIUS;
            loiter.currentAngle += dTheta;

            double targetX = (loiter.center.getX() + 0.5D) + Math.cos(loiter.currentAngle) * LOITER_RADIUS;
            double targetZ = (loiter.center.getZ() + 0.5D) + Math.sin(loiter.currentAngle) * LOITER_RADIUS;
            double targetY = loiter.center.getY();

            Vec3 desiredPos = new Vec3(targetX, targetY, targetZ);
            Vec3 toTangent = desiredPos.subtract(dragon.position());

            if (toTangent.lengthSqr() > 0.01D) {
                Vec3 velocity = toTangent.normalize().scale(loiter.speed);
                dragon.setDeltaMovement(velocity);

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

			if (active.airTarget()) {
				double distSqToAirTarget = dragon.distanceToSqr(active.dragonTarget());
				if (distSqToAirTarget <= AIR_PASS_BY_DISTANCE_SQ) {
					
					AIMovementComponent move = dragon.getAIMovement();
					
					DragonColonies.debug("NAVIGATION", "[SPIN-MEASURE] Citizen: {} | Pitch(XRot): {} | Yaw(YRot): {} | Vel: {} | BoDState: {} | BoDPathing: {}",
							citizen.getName().getString(),
							dragon.getXRot(),
							dragon.getYRot(),
							dragon.getDeltaMovement().toString(),
							move != null ? move.getState().name() : "NULL",
							move != null && move.isPathing()
					);

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
        if (pos == null || level == null) return pos;

        // Abfrage aus der persistenten Verifizierungsliste
        BlockPos cached = SAFE_AIR_CACHE.get(pos);
        if (cached != null) {
            // Bei geladenen Chunks Re-Validierung gegen eventuelle Bauwerke/Terrainveränderungen
            if (level.hasChunk(cached.getX() >> 4, cached.getZ() >> 4)) {
                if (isAirTarget(level, cached)) return cached;
                SAFE_AIR_CACHE.remove(pos);
            } else {
                // Bei ungeladenen Chunks vertrauen wir dem historisch verifizierten Punkt
                return cached;
            }
        }

        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        boolean chunkLoaded = level.hasChunk(chunkX, chunkZ);

        // Startwert-Messung: Bei ungeladenen Chunks Höhenabfrage-Bug (Y=0) durch Sicherheits-Offset verhindern
        int startY;
        if (chunkLoaded) {
            int surfaceY = level.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    pos
            ).getY();
            startY = Math.max(pos.getY() + 12, surfaceY + 12);
        } else {
            startY = pos.getY() + 25;
        }

        BlockPos candidate = new BlockPos(pos.getX(), startY, pos.getZ());
        if (!chunkLoaded) return candidate;

        // Höhen-Messschleife: Wandert bei Baumkronen/Steilhängen schrittweise nach oben, bis 2 Blöcke Freiraum garantiert sind
        int maxY = level.getMaxBuildHeight() - 2;
        while (!isAirTarget(level, candidate) && candidate.getY() < maxY) {
            candidate = candidate.above();
        }

        // Erst nach erfolgreichem Messnachweis in die Liste aufnehmen
        if (isAirTarget(level, candidate)) {
            SAFE_AIR_CACHE.put(pos, candidate);
            DragonColonies.debug("NAVIGATION", "[AIR-CACHE-SAVE] Neuer verifizierter Wegpunkt: {} -> {}", pos.toShortString(), candidate.toShortString());
        }

        return candidate;
    }

    public static void clearCache() {
        SAFE_AIR_CACHE.clear();
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