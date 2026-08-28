package net.kb150.dragoncolonies.ai;

import com.minecolonies.api.entity.ai.combat.CombatAIStates;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.kb150.dragoncolonies.DragonColonies;
import net.magister.bookofdragons.entity.ai.movement.AIMovementComponent;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.bookofdragons.entity.state.TransportMode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DragonNavigationHandler {

    private static final Map<UUID, Vec3> STUCK_POS = new HashMap<>();
    private static final Map<UUID, BlockPos> STUCK_TARGET = new HashMap<>();
    private static final Map<UUID, Integer> STUCK_TICKS = new HashMap<>();

    public static Boolean handleDragonFlight(AbstractEntityCitizen citizen, BlockPos targetPos, int distToDesired) {
        AbstractEntityAIDragonRider<?, ?> rider = AbstractEntityAIDragonRider.getRiderForCitizen(citizen);
        
        if (rider == null || rider.getAssignedDragon() == null || !rider.getAssignedDragon().isAlive()) {
            return null; // Kein Drache vorhanden -> Minecolonies übernimmt komplett
        }
		
		// --- KAMPF-RIEGEL ---
		// Wenn Wache ODER Drache ein aktives Ziel bekämpfen: 
		// Keinerlei eigene Mod-Wegpunkte setzen! BoD steuert die Flugbewegung völlig autonom.
        DragonBase dragon = rider.getAssignedDragon();
        boolean isCurrentlyRiding = citizen.getVehicle() == dragon;
		if (citizen.getTarget() != null && citizen.getTarget().isAlive() && isCurrentlyRiding) {
			return false; // Bricht Minecolonies-Bodenpfad ab, lässt BoD fliegen!
		}

        
        if (!(dragon.level() instanceof ServerLevel level) || targetPos == null) return null;

        UUID citizenId = citizen.getUUID();
        rider.setPatrolPoint(targetPos);

        boolean isPatrol = (rider.getState() == AIWorkerState.GUARD_PATROL || rider.getState() == CombatAIStates.NO_TARGET);
        
        // Startposition basierend auf Zustand (Wache zu Fuß vs. Wache auf Drache)
        Vec3 startPos = isCurrentlyRiding ? dragon.position() : citizen.position();

        // 1. RESOLVER SCHRITT: Geometrie-Check einholen
        DragonTargetResolver.TargetResult targetInfo = DragonTargetResolver.resolveTargetFromOrigin(
            level, dragon, startPos, targetPos, !isPatrol
        );

        // =========================================================================
        // FALL A: WACHE IST ZU FUSS -> Auswertung der Resolver-Daten
        // =========================================================================
        if (!isCurrentlyRiding) {
            double distToTargetSqr = citizen.distanceToSqr(targetPos.getX(), targetPos.getY(), targetPos.getZ());
            double distToDragonSqr = citizen.distanceToSqr(dragon);

            boolean canFly = targetInfo.requiredMode() == TransportMode.AIRBORNE;
            boolean mustDismountAtTarget = targetInfo.isDropZone();
            boolean isTooCloseForDismount = distToTargetSqr < 144.0D; // < 12 Blöcke
            boolean isDragonNearby = distToDragonSqr < 900.0D;       // < 30 Blöcke

            // Aufsteigen wenn fliegbar, Drache in der Nähe und keine sinnlose Kurzstrecke
            if (canFly && !(mustDismountAtTarget && isTooCloseForDismount) && isDragonNearby) {
                DragonColonies.LOGGER.info("[DRAGON-NAV] Prämisse erfüllt: Flug lohnt sich -> Aufsteigen!");
                rider.handleMountingPhase(citizen);
                return false; // Stoppt Minecolonies-Pfadfinder für Aufstieg
            }

            // Sonst: Zu Fuß weitergehen! Minecolonies übernimmt das Gehen
            return null; 
        }

        // =========================================================================
        // FALL B: WACHE SITZT IM SATTEL -> Flugsteuerung & Pfadfindung
        // =========================================================================
        citizen.getNavigation().stop();

        // 1. Ziel auf Problemzonen oder unzureichende Stehhöhe prüfen
        if (DragonTargetResolver.isKnownDismountZone(level, targetPos) || 
            (!level.canSeeSky(targetPos) && DragonTargetResolver.getVerticalHeadroom(level, targetPos, 16) < Math.ceil(dragon.getBbHeight() + 2.3D))) {
            
            DragonTargetResolver.rememberAsDismountZone(level, targetPos);
            return dismountAndSwitchToGround(citizen, dragon);
        }

        // 2. Unstuck-Watchdog
        if (checkUnstuckWatchdog(citizenId, targetPos, dragon.position(), level)) {
            return dismountAndSwitchToGround(citizen, dragon);
        }

        // 3. Flug-Pfad berechnen
        DragonPathNavigator.PathResult pathResult = DragonPathNavigator.calculatePathAdaptive(
            level, dragon, dragon.position(), targetInfo.waypoint(), targetInfo
        );

        if (pathResult.requiresDismount()) {
            DragonTargetResolver.rememberAsDismountZone(level, targetPos);
            return dismountAndSwitchToGround(citizen, dragon);
        }

        // 4. Flugroute/Wegpunkte scannen
        for (Vec3 wp : pathResult.waypoints()) {
            BlockPos wpPos = BlockPos.containing(wp);
            if (DragonTargetResolver.isKnownDismountZone(level, wpPos) || 
                (!level.canSeeSky(wpPos) && DragonTargetResolver.getVerticalHeadroom(level, wpPos, 16) < Math.ceil(dragon.getBbHeight() + 2.3D))) {
                
                DragonTargetResolver.rememberAsDismountZone(level, wpPos);
                DragonTargetResolver.rememberAsDismountZone(level, targetPos);
                return dismountAndSwitchToGround(citizen, dragon);
            }
        }

        // 5. Flug-Steuerung an den Drachen übergeben
        executeMovement(dragon, pathResult, targetInfo);

        // 6. Ankunftsprüfung
        return isTargetReached(dragon, targetPos, isPatrol);
    }

    // =========================================================================
    // PRIVATE HELFER
    // =========================================================================

    private static boolean dismountAndSwitchToGround(AbstractEntityCitizen citizen, DragonBase dragon) {
        citizen.stopRiding();
        if (dragon.getAIMovement() != null) {
            dragon.getAIMovement().clearAllWaypoints();
        }
        dragon.setTransportMode(TransportMode.GROUNDED);
        return false; 
    }

    private static boolean checkUnstuckWatchdog(UUID citizenId, BlockPos targetPos, Vec3 currentPos, ServerLevel level) {
        BlockPos lastTarget = STUCK_TARGET.get(citizenId);

        if (lastTarget == null || !targetPos.equals(lastTarget)) {
            STUCK_TARGET.put(citizenId, targetPos);
            STUCK_POS.put(citizenId, currentPos);
            STUCK_TICKS.put(citizenId, 0);
            return false;
        } 

        Vec3 lastPos = STUCK_POS.getOrDefault(citizenId, currentPos);
        if (currentPos.distanceToSqr(lastPos) < 0.04D) {
            int ticks = STUCK_TICKS.getOrDefault(citizenId, 0) + 1;
            STUCK_TICKS.put(citizenId, ticks);

            if (ticks > 200) { 
                DragonColonies.LOGGER.warn("[DRAGON-UNSTUCK] Punkt {} als unpassierbar gelernt!", targetPos.toShortString());
                DragonTargetResolver.rememberAsDismountZone(level, targetPos);
                STUCK_TICKS.put(citizenId, 0);
                return true;
            }
        } else {
            STUCK_POS.put(citizenId, currentPos);
            STUCK_TICKS.put(citizenId, 0);
        }
        return false;
    }

    private static void executeMovement(DragonBase dragon, DragonPathNavigator.PathResult pathResult, DragonTargetResolver.TargetResult targetInfo) {
        AIMovementComponent aiMove = dragon.getAIMovement();
        if (aiMove == null) return;

        TransportMode desiredMode = pathResult.requiresModeSwitch() ? pathResult.fallbackMode() : targetInfo.requiredMode();
        if (dragon.getTransportMode() != desiredMode) {
            dragon.setTransportMode(desiredMode);
        }

        if (!pathResult.waypoints().isEmpty()) {
            Vec3 nextWp = pathResult.waypoints().get(0);
            if (!aiMove.isPathing() && aiMove.getState() != AIMovementComponent.PathState.CALCULATING) {
                aiMove.setWaypoint(nextWp, 1.0D);
            }
        }
    }

    private static boolean isTargetReached(DragonBase dragon, BlockPos targetPos, boolean isPatrol) {
        double dx = dragon.getX() - (targetPos.getX() + 0.5);
        double dz = dragon.getZ() - (targetPos.getZ() + 0.5);
        
        if (isPatrol) {
            return (dx * dx + dz * dz) <= 9.0D;
        } else {
            double distSqr3D = dragon.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY() + 1.0, targetPos.getZ() + 0.5);
            return distSqr3D <= 9.0D;
        }
    }
}