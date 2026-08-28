package net.kb150.dragoncolonies.ai;

import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.bookofdragons.entity.state.TransportMode;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

public class DragonTargetResolver {

    public enum FlightTier {
        GROUNDED,   
        LOW_FLIGHT, 
        HIGH_CRUISE 
    }

    public record TargetResult(
        Vec3 waypoint,
        TransportMode requiredMode,
        FlightTier flightTier,
        BlockPos actualLandingPos,
        boolean isDropZone
    ) {}

    // =========================================================================
    // 1. EMBEDDED PERSISTENT MEMORY (Gelernte Absteig-Zonen)
    // =========================================================================
    public static class Memory extends SavedData {
        private static final String DATA_NAME = "dragoncolonies_dismount_memory";
        private final Set<BlockPos> dismountZones = new HashSet<>();

        public Memory() {}

        public static Memory get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(Memory::load, Memory::new, DATA_NAME);
        }

        public static Memory load(CompoundTag tag) {
            Memory memory = new Memory();
            ListTag list = tag.getList("DismountZones", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                memory.dismountZones.add(NbtUtils.readBlockPos(list.getCompound(i)));
            }
            return memory;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            for (BlockPos pos : dismountZones) {
                list.add(NbtUtils.writeBlockPos(pos));
            }
            tag.put("DismountZones", list);
            return tag;
        }

        public boolean isKnownDismountZone(BlockPos pos) {
            return pos != null && this.dismountZones.contains(pos);
        }

        public void rememberAsDismountZone(BlockPos pos) {
            if (pos != null && this.dismountZones.add(pos.immutable())) {
                this.setDirty();
                System.out.println("[DRAGON-RESOLVER] Block " + pos.toShortString() + " dauerhaft als Absteig-Zone gelernt!");
            }
        }

        public boolean isNearProblemZone(BlockPos centerPos, int radius) {
            if (centerPos == null || this.dismountZones.isEmpty()) return false;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    for (int y = -2; y <= 2; y++) {
                        if (this.dismountZones.contains(centerPos.offset(x, y, z))) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    public static boolean isKnownDismountZone(ServerLevel level, BlockPos pos) {
        return Memory.get(level).isKnownDismountZone(pos);
    }

    public static boolean isNearProblemZone(ServerLevel level, BlockPos pos, int radius) {
        return Memory.get(level).isNearProblemZone(pos, radius);
    }

    public static void rememberAsDismountZone(ServerLevel level, BlockPos pos) {
        Memory.get(level).rememberAsDismountZone(pos);
    }

    // =========================================================================
    // 2. HELFER: SICHERE LUFTPOSITION
    // =========================================================================
	public static BlockPos getHighAirPos(ServerLevel level, BlockPos groundPos) {
		int maxNearbyY = groundPos.getY();
		int radius = 8; // Scannt das Umfeld nach hohen Dächern und Hügeln

		for (int x = -radius; x <= radius; x += 4) {
			for (int z = -radius; z <= radius; z += 4) {
				int surfaceY = level.getHeight(
					net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 
					groundPos.getX() + x, 
					groundPos.getZ() + z
				);
				if (surfaceY > maxNearbyY) {
					maxNearbyY = surfaceY;
				}
			}
		}

		// Fliegt immer mindestens 12 Blöcke über dem HÖCHSTEN Objekt der Umgebung
		int safeFlightY = maxNearbyY + 12;
		return new BlockPos(groundPos.getX(), safeFlightY, groundPos.getZ());
	}

    // =========================================================================
    // 3. SCANNER & GEOMETRIE RESOLVER
    // =========================================================================
    public static int getVerticalHeadroom(Level level, BlockPos pos, int maxScan) {
        int headroom = 0;
        for (int i = 1; i <= maxScan; i++) {
            BlockPos checkPos = pos.above(i);
            if (!level.getBlockState(checkPos).getCollisionShape(level, checkPos).isEmpty()) {
                break;
            }
            headroom++;
        }
        return headroom;
    }

    public static TargetResult resolveTargetFromOrigin(Level level, DragonBase dragon, Vec3 startPos, BlockPos rawTarget, boolean mustLand) {
        double dist2D = Math.sqrt(startPos.distanceToSqr(rawTarget.getX() + 0.5D, startPos.y, rawTarget.getZ() + 0.5D));

        double riderExtraHeight = !dragon.getPassengers().isEmpty() 
            ? (dragon.getFirstPassenger().getBbHeight() + 0.5D) 
            : 2.3D; 

        double totalMountedHeight = dragon.getBbHeight() + riderExtraHeight;
        int neededHeadroom = (int) Math.ceil(totalMountedHeight) + 1;

        BlockPos targetPos = rawTarget;
        boolean isDropZone = false;

        int headroomAtTarget = getVerticalHeadroom(level, rawTarget, 16);
        boolean targetIsOutdoors = level.canSeeSky(rawTarget);

        if (!targetIsOutdoors && headroomAtTarget < neededHeadroom) {
            BlockPos dropZone = findDropZone(level, rawTarget, neededHeadroom);
            if (dropZone != null) {
                targetPos = dropZone;
                isDropZone = true;
            }
        }

        BlockPos originPos = BlockPos.containing(startPos);
        boolean originIsOutdoors = level.canSeeSky(originPos);
        int headroomAtOrigin = originIsOutdoors ? 15 : getVerticalHeadroom(level, originPos, 16);

        boolean finalIsOutdoors = level.canSeeSky(targetPos);
        int finalHeadroom = finalIsOutdoors ? 15 : getVerticalHeadroom(level, targetPos, 16);

        boolean canLowFly = headroomAtOrigin >= 4 && finalHeadroom >= 4;
        boolean canCruise = headroomAtOrigin >= 12 && finalHeadroom >= 12;

        boolean isCurrentlyAirborne = dragon.getTransportMode() == TransportMode.AIRBORNE;

        FlightTier tier;

        if (!canLowFly) {
            tier = FlightTier.GROUNDED;
        } else if (isCurrentlyAirborne) {
            if (mustLand && dist2D < 16.0D) {
                tier = FlightTier.GROUNDED; 
            } else if (canCruise && dist2D >= 15.0D) {
                tier = FlightTier.HIGH_CRUISE;
            } else {
                tier = FlightTier.LOW_FLIGHT;
            }
        } else {
            if (mustLand || dist2D < 12.0D) {
                tier = FlightTier.GROUNDED; 
            } else if (dist2D < 35.0D || !canCruise) {
                tier = FlightTier.LOW_FLIGHT; 
            } else {
                tier = FlightTier.HIGH_CRUISE; 
            }
        }

        switch (tier) {
            case LOW_FLIGHT -> {
                double lowFlyHeight = finalIsOutdoors ? 4.0D : Math.min(finalHeadroom - 1, 4.0D);
                Vec3 lowVec = new Vec3(targetPos.getX() + 0.5D, targetPos.getY() + lowFlyHeight, targetPos.getZ() + 0.5D);
                return new TargetResult(lowVec, TransportMode.AIRBORNE, tier, targetPos, isDropZone);
            }
            case HIGH_CRUISE -> {
                double highFlyHeight = finalIsOutdoors ? 15.0D : Math.min(finalHeadroom - 1, 12.0D);
                Vec3 highVec = new Vec3(targetPos.getX() + 0.5D, targetPos.getY() + highFlyHeight, targetPos.getZ() + 0.5D);
                return new TargetResult(highVec, TransportMode.AIRBORNE, tier, targetPos, isDropZone);
            }
            default -> { 
                Vec3 groundVec = new Vec3(targetPos.getX() + 0.5D, targetPos.getY() + 1.0D, targetPos.getZ() + 0.5D);
                return new TargetResult(groundVec, TransportMode.GROUNDED, tier, targetPos, isDropZone);
            }
        }
    }

    private static BlockPos findDropZone(Level level, BlockPos center, int neededHeadroom) {
        for (int r = 2; r <= 10; r += 2) {
            for (int x = -r; x <= r; x += r) {
                for (int z = -r; z <= r; z += r) {
                    BlockPos check = center.offset(x, 0, z);
                    if (level.canSeeSky(check) || getVerticalHeadroom(level, check, 16) >= neededHeadroom) {
                        return check;
                    }
                }
            }
        }
        return center;
    }
}