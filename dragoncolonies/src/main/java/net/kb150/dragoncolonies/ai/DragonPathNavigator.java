package net.kb150.dragoncolonies.ai;

import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.bookofdragons.entity.state.TransportMode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DragonPathNavigator {

    public record PathResult(
        List<Vec3> waypoints,
        boolean requiresModeSwitch,
        TransportMode fallbackMode,
        boolean requiresDismount
    ) {}

    private record CandidatePath(List<Vec3> waypoints, double totalDistance) {}

    public static PathResult calculatePathAdaptive(ServerLevel level, DragonBase dragon, Vec3 start, Vec3 target, DragonTargetResolver.TargetResult targetInfo) {
        // 1. Schwellenwert-Check (Immer zuerst Direktweg prüfen)
        if (isPathClear(level, dragon, start, target)) {
            return new PathResult(List.of(target), false, targetInfo.requiredMode(), false);
        }

        PathfindingQuality quality = getCurrentQuality(level);

        // ==========================================
        // 1. LOW QUALITY (Höchstlast: Express-Check)
        // ==========================================
        if (quality == PathfindingQuality.LOW) {
            Vec3 groundTarget = new Vec3(target.x, targetInfo.actualLandingPos().getY() + 1.0D, target.z);
            if (isPathClear(level, dragon, start, groundTarget)) {
                return new PathResult(List.of(groundTarget), true, TransportMode.GROUNDED, false);
            }
            return new PathResult(List.of(target), true, TransportMode.GROUNDED, true);
        }

        BlockPos startPos = BlockPos.containing(start);
        boolean startOutdoors = level.canSeeSky(startPos);
        int headroom = startOutdoors ? 15 : DragonTargetResolver.getVerticalHeadroom(level, startPos, 16);

        // ==========================================
        // 2. HIGH QUALITY (Flüssig: Shortest-Path Solver)
        // ==========================================
        if (quality == PathfindingQuality.HIGH) {
            List<CandidatePath> validCandidates = new ArrayList<>();

            // A) Oben (Drübersteigen)
            if (headroom >= 6) {
                double climbHeight = Math.min(headroom - 2, 12.0D);
                Vec3 climbStart = new Vec3(start.x, Math.max(start.y, target.y) + climbHeight, start.z);
                Vec3 climbTarget = new Vec3(target.x, Math.max(start.y, target.y) + climbHeight, target.z);
                addCandidateIfValid(level, dragon, start, List.of(climbStart, climbTarget, target), validCandidates);
            }

            // B) Flankieren (Links & Rechts)
            Vec3 dir = target.subtract(start).normalize();
            Vec3 leftOffset = new Vec3(-dir.z, 0, dir.x).scale(10.0D);
            Vec3 rightOffset = new Vec3(dir.z, 0, -dir.x).scale(10.0D);

            addCandidateIfValid(level, dragon, start, List.of(start.add(leftOffset), target.add(leftOffset), target), validCandidates);
            addCandidateIfValid(level, dragon, start, List.of(start.add(rightOffset), target.add(rightOffset), target), validCandidates);

            // C) Diagonale Aufstiegswinkel (3D Diagonalen)
            if (headroom >= 6) {
                double climbHeight = Math.min(headroom - 2, 8.0D);
                Vec3 diagUpLeft = leftOffset.add(0, climbHeight, 0);
                Vec3 diagUpRight = rightOffset.add(0, climbHeight, 0);

                addCandidateIfValid(level, dragon, start, List.of(start.add(diagUpLeft), target.add(diagUpLeft), target), validCandidates);
                addCandidateIfValid(level, dragon, start, List.of(start.add(diagUpRight), target.add(diagUpRight), target), validCandidates);
            }

            // Wähle den Pfad mit der KÜRZESTEN echten Gesamtdistanz!
            if (!validCandidates.isEmpty()) {
                CandidatePath best = validCandidates.stream()
                        .min(Comparator.comparingDouble(CandidatePath::totalDistance))
                        .orElse(validCandidates.get(0));
                return new PathResult(best.waypoints(), false, TransportMode.AIRBORNE, false);
            }
        }

        // ==========================================
        // 3. MEDIUM QUALITY (Normallast: Erste valide Route nimmt das Rennen)
        // ==========================================
        // Oben
        if (headroom >= 6) {
            double climbHeight = Math.min(headroom - 2, 12.0D);
            Vec3 climbStart = new Vec3(start.x, Math.max(start.y, target.y) + climbHeight, start.z);
            Vec3 climbTarget = new Vec3(target.x, Math.max(start.y, target.y) + climbHeight, target.z);
            if (isSegmentClear(level, dragon, start, climbStart, climbTarget, target)) {
                return new PathResult(List.of(climbStart, climbTarget, target), false, TransportMode.AIRBORNE, false);
            }
        }

        // Links / Rechts
        Vec3 dir = target.subtract(start).normalize();
        Vec3 leftOffset = new Vec3(-dir.z, 0, dir.x).scale(10.0D);
        Vec3 rightOffset = new Vec3(dir.z, 0, -dir.x).scale(10.0D);

        if (isSegmentClear(level, dragon, start, start.add(leftOffset), target.add(leftOffset), target)) {
            return new PathResult(List.of(start.add(leftOffset), target.add(leftOffset), target), false, TransportMode.AIRBORNE, false);
        }
        if (isSegmentClear(level, dragon, start, start.add(rightOffset), target.add(rightOffset), target)) {
            return new PathResult(List.of(start.add(rightOffset), target.add(rightOffset), target), false, TransportMode.AIRBORNE, false);
        }

        // Bodenwechsel Fallback
        Vec3 groundTarget = new Vec3(target.x, targetInfo.actualLandingPos().getY() + 1.0D, target.z);
        if (isPathClear(level, dragon, start, groundTarget)) {
            return new PathResult(List.of(groundTarget), true, TransportMode.GROUNDED, false);
        }

        // Dismount Failsafe
        return new PathResult(List.of(target), true, TransportMode.GROUNDED, true);
    }

    private static void addCandidateIfValid(ServerLevel level, DragonBase dragon, Vec3 start, List<Vec3> waypoints, List<CandidatePath> candidates) {
        if (isSegmentClear(level, dragon, start, waypoints.toArray(new Vec3[0]))) {
            double totalDist = 0.0D;
            Vec3 current = start;
            for (Vec3 wp : waypoints) {
                totalDist += current.distanceTo(wp);
                current = wp;
            }
            candidates.add(new CandidatePath(waypoints, totalDist));
        }
    }

    private static boolean isSegmentClear(ServerLevel level, DragonBase dragon, Vec3 start, Vec3... points) {
        Vec3 current = start;
        for (Vec3 p : points) {
            if (!isPathClear(level, dragon, current, p)) return false;
            current = p;
        }
        return true;
    }

    public static boolean isPathClear(ServerLevel level, DragonBase dragon, Vec3 start, Vec3 end) {
        double dist = start.distanceTo(end);
        if (dist < 0.5D) return true;

        int steps = (int) Math.ceil(dist * 2.0D);
        Vec3 stepVec = end.subtract(start).scale(1.0D / steps);

        double halfWidth = dragon.getBbWidth() / 2.0D;

        // --- REITER-HÖHEN-KOMPENSATION ---
        // Prüft, ob ein Passagier aufsitzt (Bürger-Höhe + Sattel-Offset)
        double riderExtraHeight = !dragon.getPassengers().isEmpty()
                ? (dragon.getFirstPassenger().getBbHeight() + 0.5D)
                : 2.3D; // Fallback: ~1.8m Bürger + 0.5m Sattelhöhe

        double totalMountedHeight = dragon.getBbHeight() + riderExtraHeight;

        for (int i = 1; i <= steps; i++) {
            Vec3 checkPos = start.add(stepVec.scale(i));
            AABB checkBox = new AABB(
                checkPos.x - halfWidth, checkPos.y, checkPos.z - halfWidth,
                checkPos.x + halfWidth, checkPos.y + totalMountedHeight, checkPos.z + halfWidth
            );

            if (!level.noCollision(dragon, checkBox)) {
                return false;
            }
        }
        return true;
    }

    public enum PathfindingQuality { HIGH, MEDIUM, LOW }

    public static PathfindingQuality getCurrentQuality(ServerLevel level) {
        float mspt = level.getServer().getAverageTickTime();
        if (mspt < 30.0F) return PathfindingQuality.HIGH;
        if (mspt < 45.0F) return PathfindingQuality.MEDIUM;
        return PathfindingQuality.LOW;
    }
}