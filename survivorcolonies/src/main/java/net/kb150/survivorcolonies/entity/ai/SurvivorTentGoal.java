package net.kb150.survivorcolonies.entity.ai;

import net.kb150.survivorcolonies.SurvivorColonies;
import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SurvivorTentGoal extends Goal {
    private final SurvivorEntity survivor;
    private final List<BlockPos> blocksToBuild = new ArrayList<>();

    private State state = State.IDLE;
    private int actionTimer = 0;
    private int hiddenSafetyTimer = 0;
    private int sleepParticleTimer = 0;
    private boolean tentWasDestroyed = false;
    private int failedCooldown = 0;

    private static final int MAX_HIDDEN_TICKS = 24000;
    private static final int SLEEP_PARTICLE_INTERVAL = 60;

    private static final Set<Item> WOOL_ITEMS = Set.of(
        Items.WHITE_WOOL, Items.ORANGE_WOOL, Items.MAGENTA_WOOL, Items.LIGHT_BLUE_WOOL,
        Items.YELLOW_WOOL, Items.LIME_WOOL, Items.PINK_WOOL, Items.GRAY_WOOL,
        Items.LIGHT_GRAY_WOOL, Items.CYAN_WOOL, Items.PURPLE_WOOL, Items.BLUE_WOOL,
        Items.BROWN_WOOL, Items.GREEN_WOOL, Items.RED_WOOL, Items.BLACK_WOOL
    );

    private enum State { IDLE, BUILDING, STANDBY, ENTERING, SLEEPING }

    public SurvivorTentGoal(SurvivorEntity survivor) {
        this.survivor = survivor;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    // Proxy-Getter für andere KI-Goals (Scavenge/Campfire), um Inkompatibilitäten zu vermeiden.
    // Greifen direkt auf NBT zu, ohne KI-Zustände (IDLE) zu prüfen.
    public BlockPos getTentApproachPos() { return survivor.getKnownTentPos(); }
    public BlockPos getTentAnchorPos() {
        BlockPos known = survivor.getKnownTentPos();
        return known != null ? getTentInside(known) : null;
    }

    @Override
    public boolean canUse() {
        if (survivor.isPassenger() || survivor.getActivity() == SurvivorActivity.COMBAT) return false;

        if (this.failedCooldown > 0) {
            this.failedCooldown--;
            return false;
        }

        if (this.state == State.STANDBY && !isTentIntact()) {
            notifyDestroyed();
        }

        SurvivorActivity act = survivor.getActivity();
        boolean needsHealing = survivor.getHealth() < survivor.getMaxHealth();
        boolean tentNeedsWork = survivor.getKnownTentPos() == null || !isTentIntact();

        return switch (this.state) {
            case IDLE -> tentNeedsWork || act == SurvivorActivity.SLEEPING || needsHealing;
            case BUILDING, ENTERING -> true;
            case STANDBY -> act == SurvivorActivity.SLEEPING || needsHealing;
            case SLEEPING -> act == SurvivorActivity.SLEEPING || needsHealing;
        };
    }

    @Override
    public void start() {
        if (this.state == State.IDLE) {
            if (!planTentLocation()) {
                this.failedCooldown = 100;
                this.state = State.STANDBY;
                return;
            }
            this.actionTimer = 0;
        }

        if (this.state == State.STANDBY) {
            SurvivorActivity act = survivor.getActivity();
            boolean needsHealing = survivor.getHealth() < survivor.getMaxHealth();
            if (act == SurvivorActivity.SLEEPING || needsHealing) {
                this.state = State.ENTERING;
                this.actionTimer = 0;
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (survivor.getActivity() == SurvivorActivity.COMBAT) return false;

        boolean isFullyHealed = survivor.getHealth() >= survivor.getMaxHealth();

        return switch (state) {
            case BUILDING -> !blocksToBuild.isEmpty();
            case ENTERING, SLEEPING -> (survivor.level().isNight() || !isFullyHealed) 
                    && hiddenSafetyTimer < MAX_HIDDEN_TICKS 
                    && !tentWasDestroyed;
            default -> false;
        };
    }

    @Override
    public void tick() {
        BlockPos approach = survivor.getKnownTentPos();

        switch (state) {
            case BUILDING -> {
                if (approach != null && survivor.distanceToSqr(approach.getX() + 0.5D, approach.getY(), approach.getZ() + 0.5D) > 2.0D) {
                    survivor.getNavigation().moveTo(approach.getX() + 0.5D, approach.getY(), approach.getZ() + 0.5D, 1.0D);
                    return;
                }

                survivor.getNavigation().stop();
                actionTimer++;
                if (actionTimer >= 6) {
                    actionTimer = 0;
                    if (!blocksToBuild.isEmpty()) {
                        BlockPos targetPos = blocksToBuild.remove(0);

                        if (targetPos.equals(survivor.blockPosition()) || targetPos.equals(survivor.blockPosition().above())) {
                            if (approach != null) survivor.setPos(approach.getX() + 0.5D, approach.getY(), approach.getZ() + 0.5D);
                        }

                        if (survivor.level().getBlockState(targetPos).canBeReplaced()) {
                            tryConsumeOneWool();
                            Block woolBlock = getWoolColorForUUID(survivor.getUUID());
                            survivor.level().setBlockAndUpdate(targetPos, woolBlock.defaultBlockState());

                            survivor.swing(InteractionHand.MAIN_HAND);
                            survivor.level().playSound(null, targetPos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                            survivor.getLookControl().setLookAt(targetPos.getX() + 0.5D, targetPos.getY() + 0.5D, targetPos.getZ() + 0.5D);
                        }
                    } else {
                        boolean needsHealing = survivor.getHealth() < survivor.getMaxHealth();
                        state = (survivor.getActivity() == SurvivorActivity.SLEEPING || needsHealing) ? State.ENTERING : State.STANDBY;
                        actionTimer = 0;
                    }
                }
            }
            case ENTERING -> {
                double distSq = (approach != null) ? survivor.distanceToSqr(approach.getX() + 0.5D, approach.getY(), approach.getZ() + 0.5D) : Double.MAX_VALUE;
                if (isInsideTent() || distSq < 1.5D || survivor.getNavigation().isDone()) {
                    enterTent();
                } else if (approach != null) {
                    survivor.getNavigation().moveTo(approach.getX() + 0.5D, approach.getY(), approach.getZ() + 0.5D, 1.0D);
                }
            }
            case SLEEPING -> {
                if (approach != null) {
                    BlockPos inside = getTentInside(approach);
                    survivor.setPos(inside.getX() + 0.5D, inside.getY(), inside.getZ() + 0.5D);

                    if (survivor.tickCount % 20 == 0 && survivor.getHealth() < survivor.getMaxHealth()) survivor.heal(1.0F);

                    hiddenSafetyTimer++;
                    sleepParticleTimer++;
                    if (sleepParticleTimer >= SLEEP_PARTICLE_INTERVAL) {
                        sleepParticleTimer = 0;
                        survivor.spawnSleepParticles(inside);
                    }
                }

                if (!isTentIntact()) tentWasDestroyed = true;
            }
            case STANDBY, IDLE -> {}
        }
    }

    @Override
    public void stop() {
        exitTent();

        if (tentWasDestroyed) {
            notifyDestroyed();
        } else if (state == State.ENTERING || state == State.SLEEPING) {
            state = State.STANDBY;
        }
    }

    private void enterTent() {
        BlockPos approach = survivor.getKnownTentPos();
        if (approach == null) return;
        
        BlockPos inside = getTentInside(approach);
        survivor.getNavigation().stop();
        
        // Zwingend erst Position setzen, dann Partikel/Sichtbarkeit feuern, 
        // da Vanilla sonst Effekte an der alten Anmarschposition berechnet.
        survivor.setPos(inside.getX() + 0.5D, inside.getY(), inside.getZ() + 0.5D);
        survivor.setHiddenInTent(true);
        
        this.state = State.SLEEPING;
        this.hiddenSafetyTimer = 0;
        this.sleepParticleTimer = 0;
    }

    private void exitTent() {
        if (this.state != State.SLEEPING && !survivor.isInvisible()) return;

        BlockPos approach = survivor.getKnownTentPos();
        
        // Atomare Sperre: Verhindert das Abschalten der Unsichtbarkeit, 
        // wenn der NPC nicht physisch aus dem Wollblock bewegt werden kann.
        if (approach == null) return;

        survivor.getNavigation().stop();
        survivor.setPos(approach.getX() + 0.5D, approach.getY(), approach.getZ() + 0.5D);
        survivor.setHiddenInTent(false);
    }

    public boolean isInsideTent() {
        BlockPos known = survivor.getKnownTentPos();
        if (known == null) return false;
        return survivor.blockPosition().distSqr(getTentInside(known)) <= 2;
    }

    public boolean isSleeping() { return this.state == State.SLEEPING; }

    public boolean isTentIntact() {
        BlockPos known = survivor.getKnownTentPos();
        if (known == null) return false;
        
        BlockPos roof = getTentInside(known).above();
        if (!survivor.level().hasChunkAt(roof)) return true;
        return survivor.level().getBlockState(roof).is(BlockTags.WOOL);
    }

    @Override
    public boolean isInterruptable() {
        return this.state != State.SLEEPING || survivor.getHealth() >= survivor.getMaxHealth();
    }

	public void notifyDestroyed() {
		this.state = State.IDLE;
		this.blocksToBuild.clear();
		this.tentWasDestroyed = false;
	}

    private boolean tryConsumeOneWool() {
        var inv = survivor.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && WOOL_ITEMS.contains(stack.getItem())) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

	private boolean planTentLocation() {
		BlockPos knownTent = survivor.getKnownTentPos();
		BlockPos knownCampfire = survivor.getKnownCampfirePos();

		if (knownTent != null) {
			// Erst wenn die Strukturprüfungen fehlschlagen, wird das alte Zelt verworfen
			if (tryRepairExistingTent(knownTent, knownCampfire)) return true;
			
			dismantleOldTent(knownTent);
			survivor.setKnownTentPos(null);
		}

		BlockPos current = survivor.blockPosition();
		if (checkAndSetTentAt(current, knownCampfire)) {
			survivor.setKnownTentPos(current);
			return true;
		}

		for (BlockPos pos : BlockPos.betweenClosed(current.offset(-6, -2, -6), current.offset(6, 2, 6))) {
			if (!pos.equals(current) && checkAndSetTentAt(pos, knownCampfire)) {
				survivor.setKnownTentPos(pos);
				return true;
			}
		}
		return false;
	}

    private boolean tryRepairExistingTent(BlockPos tentPos, BlockPos knownCampfire) {
        if (!survivor.level().hasChunkAt(tentPos)) {
            this.state = State.STANDBY;
            return true;
        }

        Map<BlockPos, BlockState> layout = getTentLayout(tentPos, survivor);
        this.blocksToBuild.clear();
        boolean validStructure = true;

        for (Map.Entry<BlockPos, BlockState> entry : layout.entrySet()) {
            BlockState currentState = survivor.level().getBlockState(entry.getKey());
            if (currentState.is(BlockTags.WOOL)) continue;
            
            if (currentState.canBeReplaced()) {
                this.blocksToBuild.add(entry.getKey());
            } else { 
                validStructure = false; 
                break; 
            }
        }

        if (validStructure) {
            this.state = this.blocksToBuild.isEmpty() ? State.STANDBY : State.BUILDING;
            return true;
        }
        return false;
    }

    private boolean checkAndSetTentAt(BlockPos center, BlockPos knownCampfire) {
        Map<BlockPos, BlockState> layout = getTentLayout(center, survivor);
        for (BlockPos worldPos : layout.keySet()) {
            if (!survivor.level().getBlockState(worldPos).canBeReplaced() || !survivor.level().getFluidState(worldPos).isEmpty()) return false;
            if (knownCampfire != null && (worldPos.equals(knownCampfire) || worldPos.equals(knownCampfire.above()))) return false;
        }

        if (!survivor.level().getFluidState(center).isEmpty() || !survivor.level().getBlockState(center.below()).isFaceSturdy(survivor.level(), center.below(), Direction.UP)) return false;

        this.blocksToBuild.clear();
        this.blocksToBuild.addAll(layout.keySet());
        this.state = State.BUILDING;
        return true;
    }

    private void dismantleOldTent(BlockPos oldTentPos) {
        if (oldTentPos == null) return;
        for (BlockPos worldPos : getTentLayout(oldTentPos, survivor).keySet()) {
            if (survivor.level().getBlockState(worldPos).is(BlockTags.WOOL)) {
                survivor.level().destroyBlock(worldPos, true, survivor);
            }
        }
    }

    public BlockPos getTentInside(BlockPos tentPos) {
        return tentPos.relative(getDeterministicDirection(tentPos), 2);
    }

    public Map<BlockPos, BlockState> getTentLayout(BlockPos tentPos, SurvivorEntity survivor) {
        Map<BlockPos, BlockState> layout = new HashMap<>();
        BlockState woolState = getWoolColorForUUID(survivor.getUUID()).defaultBlockState();
        Direction dir = getDeterministicDirection(tentPos);
        Direction left = dir.getCounterClockWise();
        Direction right = dir.getClockWise();

        for (int i = 1; i <= 3; i++) {
            BlockPos forwardPos = tentPos.relative(dir, i);
            layout.put(forwardPos.relative(left), woolState);
            layout.put(forwardPos.relative(right), woolState);
            layout.put(forwardPos.above(), woolState);
        }
        layout.put(tentPos.relative(dir, 3), woolState);
        return layout;
    }

    private Direction getDeterministicDirection(BlockPos pos) {
        Direction[] horizontals = new Direction[]{ Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };
        return horizontals[Math.floorMod(pos.hashCode(), horizontals.length)];
    }

    private Block getWoolColorForUUID(UUID uuid) {
        Block[] wools = {
            Blocks.WHITE_WOOL, Blocks.ORANGE_WOOL, Blocks.MAGENTA_WOOL, Blocks.LIGHT_BLUE_WOOL,
            Blocks.YELLOW_WOOL, Blocks.LIME_WOOL, Blocks.PINK_WOOL, Blocks.GRAY_WOOL,
            Blocks.LIGHT_GRAY_WOOL, Blocks.CYAN_WOOL, Blocks.PURPLE_WOOL, Blocks.BLUE_WOOL,
            Blocks.BROWN_WOOL, Blocks.GREEN_WOOL, Blocks.RED_WOOL, Blocks.BLACK_WOOL
        };
        return wools[Math.abs(uuid.hashCode()) % wools.length];
    }
}