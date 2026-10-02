package net.kb150.survivorcolonies.entity.ai;

import net.kb150.survivorcolonies.SurvivorColonies;
import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.EnumSet;

public class SurvivorHarvestRemainsGoal extends Goal {
    private final SurvivorEntity survivor;
    private BlockPos targetBlockPos;
    private BlockPos targetStandPos;
    private int miningTicks = 0;
    private int navigateTicks = 0;

    private static final int MAX_NAVIGATE_TICKS = 160; // 8 Sekunden maximale Laufzeit
    private static final double MAX_MINE_DISTANCE_SQ = 6.25D; // 2.5 Blöcke (2.5 * 2.5) Schlagdistanz

    private BlockPos unreachablePos = null;
    private int unreachableCooldown = 0;

    private static final ResourceLocation ZOMBIE_REMAINS_ID = new ResourceLocation("zombieremains", "zombieremains");

    public SurvivorHarvestRemainsGoal(SurvivorEntity survivor) {
        this.survivor = survivor;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.unreachableCooldown > 0) {
            this.unreachableCooldown--;
        } else {
            this.unreachablePos = null;
        }

        if (this.survivor.isPassenger() 
                || this.survivor.getTarget() != null 
                || this.survivor.getTradingPlayer() != null) {
            return false;
        }

        SurvivorActivity activity = this.survivor.getActivity();
        if (activity == SurvivorActivity.COMBAT || activity == SurvivorActivity.SLEEPING) {
            return false;
        }

        if (this.survivor.getRandom().nextInt(15) != 0) return false;

        BlockPos current = this.survivor.blockPosition();

        for (BlockPos pos : BlockPos.betweenClosed(current.offset(-10, -2, -10), current.offset(10, 2, 10))) {
            if (this.unreachablePos != null && this.unreachablePos.equals(pos)) {
                continue;
            }

            if (!this.survivor.level().hasChunkAt(pos)) continue;

            BlockState state = this.survivor.level().getBlockState(pos);
            if (isHarvestableBlock(state.getBlock())) {
                BlockPos standPos = findStandPos(pos);
                if (standPos != null) {
                    this.targetBlockPos = pos.immutable();
                    this.targetStandPos = standPos.immutable();
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void start() {
        if (this.targetBlockPos != null && this.targetStandPos != null) {
            this.miningTicks = 40;
            this.navigateTicks = 0;
            this.survivor.getNavigation().moveTo(
                this.targetStandPos.getX() + 0.5D, 
                this.targetStandPos.getY(), 
                this.targetStandPos.getZ() + 0.5D, 
                1.0D
            );
        }
    }

    @Override
    public void tick() {
        if (this.targetBlockPos == null) return;

        double distSq = this.survivor.distanceToSqr(Vec3.atCenterOf(this.targetBlockPos));
        boolean inMiningRange = distSq <= MAX_MINE_DISTANCE_SQ;
        boolean navDone = this.survivor.getNavigation().isDone();

        // WENN ER IN REICHWEITE IST ODER DIE NAVIGATION BEREITS VOR DEM BLOCK STEHT:
        // SOFORT ANHALTEN UND ABBAUEN! KEIN WARTEN, KEIN FEILSCHEN UM MILLIMETER!
        if (inMiningRange || (navDone && distSq <= 8.0D)) {
            this.survivor.getNavigation().stop();
            this.survivor.getLookControl().setLookAt(Vec3.atCenterOf(this.targetBlockPos));

            this.miningTicks--;

            if (this.miningTicks % 5 == 0) {
                this.survivor.swing(InteractionHand.MAIN_HAND);
                this.survivor.level().playSound(
                    null, 
                    this.targetBlockPos, 
                    SoundEvents.GRAVEL_BREAK, 
                    SoundSource.BLOCKS, 
                    0.5F, 
                    0.8F
                );
            }

            if (this.miningTicks <= 0) {
                if (!this.survivor.level().isClientSide) {
                    this.survivor.level().destroyBlock(this.targetBlockPos, true, this.survivor);
                }
                this.targetBlockPos = null; // Sauberes Beenden
            }
        } else {
            // Er ist noch nicht da: Weiterlaufen!
            this.navigateTicks++;

            if (navDone || this.navigateTicks % 15 == 0) {
                if (this.targetStandPos != null) {
                    this.survivor.getNavigation().moveTo(
                        this.targetStandPos.getX() + 0.5D, 
                        this.targetStandPos.getY(), 
                        this.targetStandPos.getZ() + 0.5D, 
                        1.0D
                    );
                }
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (this.targetBlockPos == null) return false;

        // Wenn er abbaut (miningTicks läuft), bricht kein Navigations-Timeout ab!
        if (this.miningTicks < 40 && this.miningTicks > 0) {
            return isHarvestableBlock(this.survivor.level().getBlockState(this.targetBlockPos).getBlock());
        }

        if (this.navigateTicks >= MAX_NAVIGATE_TICKS) {
            this.unreachablePos = this.targetBlockPos;
            this.unreachableCooldown = 160; // 8 Sekunden Cooldown
            return false;
        }

        return this.survivor.getTarget() == null 
            && isHarvestableBlock(this.survivor.level().getBlockState(this.targetBlockPos).getBlock());
    }

    @Override
    public void stop() {
        this.survivor.getNavigation().stop();
        this.targetBlockPos = null;
        this.targetStandPos = null;
        this.miningTicks = 0;
        this.navigateTicks = 0;
    }

    private BlockPos findStandPos(BlockPos target) {
        BlockPos bestPos = null;
        double bestDistSq = Double.MAX_VALUE;

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = target.relative(dir);
            if (canStandAt(neighbor)) {
                double distSq = this.survivor.distanceToSqr(neighbor.getX() + 0.5D, neighbor.getY(), neighbor.getZ() + 0.5D);
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    bestPos = neighbor;
                }
            } else if (canStandAt(neighbor.above())) {
                double distSq = this.survivor.distanceToSqr(neighbor.getX() + 0.5D, neighbor.getY() + 1.0D, neighbor.getZ() + 0.5D);
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    bestPos = neighbor.above();
                }
            } else if (canStandAt(neighbor.below())) {
                double distSq = this.survivor.distanceToSqr(neighbor.getX() + 0.5D, neighbor.getY() - 1.0D, neighbor.getZ() + 0.5D);
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    bestPos = neighbor.below();
                }
            }
        }
        return bestPos;
    }

    private boolean canStandAt(BlockPos pos) {
        Level level = this.survivor.level();
        if (!level.hasChunkAt(pos)) return false;

        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockState ground = level.getBlockState(pos.below());

        boolean feetFree = feet.isAir() || feet.canBeReplaced();
        boolean headFree = head.isAir() || head.canBeReplaced();
        boolean groundSolid = ground.isFaceSturdy(level, pos.below(), Direction.UP);

        return feetFree && headFree && groundSolid;
    }

    private boolean isHarvestableBlock(Block block) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        return id != null && id.equals(ZOMBIE_REMAINS_ID);
    }
}