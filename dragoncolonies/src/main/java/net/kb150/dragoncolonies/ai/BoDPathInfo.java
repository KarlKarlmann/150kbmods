package net.kb150.dragoncolonies.ai;

import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.world.level.pathfinder.Path;

/**
 * Kleine Bridge-Datenablage fuer das von Book of Dragons berechnete Ergebnis.
 * Keine eigene Navigation.
 */
public final class BoDPathInfo {

    public record Result(Vec3 target, Vec3 endPoint, int nodeCount) {
    }

    private static final Map<UUID, Result> RESULTS = new ConcurrentHashMap<>();

    private BoDPathInfo() {
    }

    public static void begin(DragonBase dragon, Vec3 target) {
        RESULTS.remove(dragon.getUUID());
    }

    public static void record(DragonBase dragon, Vec3 target, Path path) {
        Vec3 endPoint = null;
        int nodeCount = 0;

        if (path != null) {
			nodeCount = path.getNodeCount();
			if (nodeCount > 0) {
				var node = path.getNode(nodeCount - 1);
				endPoint = new Vec3(
						node.x + 0.5D,
						node.y,
						node.z + 0.5D
				);
			}
        }

        RESULTS.put(
                dragon.getUUID(),
                new Result(target, endPoint, nodeCount)
        );
    }

    public static Result get(DragonBase dragon) {
        return RESULTS.get(dragon.getUUID());
    }

    public static void clear(DragonBase dragon) {
        RESULTS.remove(dragon.getUUID());
    }
}
