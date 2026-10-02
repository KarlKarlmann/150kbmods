package net.kb150.survivorcolonies.entity.ai;

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

/**
 * Baut ein Wollzelt als dauerhafte Basis auf.
 * tentPos ist die feste Einstiegsposition (vor dem Zelt im Freien).
 * Das Zeltinnere sowie die 10 Zeltblöcke werden deterministisch vor diesem Einstieg platziert.
 */
public class SurvivorTentGoal extends Goal {
    private final SurvivorEntity survivor;
    private final List<BlockPos> blocksToBuild = new ArrayList<>();

    private BlockPos tentInside = null;
    private BlockPos tentApproach = null; // Identisch mit tentPos (fester Einstiegs- und Warteplatz)
    private BlockPos criticalRoofBlock = null; // Dach direkt über tentInside
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

    private enum State {
        IDLE,        // Kein Zelt vorhanden / wird neu gesucht
        BUILDING,    // Wird gerade aufgebaut oder repariert
        STANDBY,     // Zelt steht, Survivor macht etwas anderes
        ENTERING,    // Läuft zum Punkt VOR dem Zelt, um reinzuschlüpfen
        SLEEPING     // Schläft unsichtbar & unverwundbar im Zelt
    }

    public SurvivorTentGoal(SurvivorEntity survivor) {
        this.survivor = survivor;
        // Flag.JUMP ENTFERNT! Verhindert, dass FloatGoal (Prio 0) dieses Goal im Wasser abbricht.
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (survivor.isPassenger()) return false;

        if (this.failedCooldown > 0) {
            this.failedCooldown--;
            return false;
        }

        if (this.state == State.STANDBY && !isTentIntact()) {
            notifyDestroyed();
        }

        SurvivorActivity act = survivor.getActivity();
        return switch (this.state) {
            case IDLE -> act != SurvivorActivity.COMBAT;
            case BUILDING, ENTERING -> true;
            case STANDBY -> act == SurvivorActivity.SLEEPING;
            case SLEEPING -> act == SurvivorActivity.SLEEPING;
        };
    }

    @Override
    public void start() {
        net.kb150.survivorcolonies.SurvivorColonies.LOGGER.info("[TENT-GOAL] START called! State: {}, Activity: {}", this.state, survivor.getActivity());
        if (this.state == State.IDLE) {
            if (!planTentLocation()) {
                this.failedCooldown = 100;
                this.state = State.STANDBY;
                return;
            }
            this.actionTimer = 0;
            return;
        }

        if (this.state == State.STANDBY) {
            if (survivor.getActivity() == SurvivorActivity.SLEEPING) {
                this.state = State.ENTERING;
                this.actionTimer = 0;
            }
            return;
        }
    }

    private boolean planTentLocation() {
        BlockPos knownTent = survivor.getKnownTentPos();
        BlockPos knownCampfire = survivor.getKnownCampfirePos();

        if (knownTent != null) {
            if (tryRepairExistingTent(knownTent, knownCampfire)) {
                return true;
            } else {
                dismantleOldTent(knownTent);
                survivor.setKnownTentPos(null);
            }
        }

        BlockPos current = survivor.blockPosition();

        if (checkAndSetTentAt(current, knownCampfire)) {
            survivor.setKnownTentPos(this.tentApproach);
            return true;
        }

        for (BlockPos pos : BlockPos.betweenClosed(current.offset(-6, -2, -6), current.offset(6, 2, 6))) {
            if (pos.equals(current)) continue;
            if (checkAndSetTentAt(pos, knownCampfire)) {
                survivor.setKnownTentPos(this.tentApproach);
                return true;
            }
        }

        return false;
    }

    private boolean tryRepairExistingTent(BlockPos tentPos, BlockPos knownCampfire) {
        if (!survivor.level().hasChunkAt(tentPos)) {
            this.tentApproach = tentPos;
            this.tentInside = getTentInside(tentPos);
            this.criticalRoofBlock = this.tentInside.above();
            this.state = State.STANDBY;
            return true;
        }

        Map<BlockPos, BlockState> layout = getTentLayout(tentPos, survivor);

        this.blocksToBuild.clear();
        boolean validStructure = true;

        for (Map.Entry<BlockPos, BlockState> entry : layout.entrySet()) {
            BlockPos worldPos = entry.getKey();
            BlockState currentState = survivor.level().getBlockState(worldPos);

            if (isWoolBlock(currentState.getBlock())) {
                continue; // Steht bereits korrekt
            } else if (currentState.canBeReplaced()) {
                this.blocksToBuild.add(worldPos); // Fehlt
            } else {
                validStructure = false; // Blockiert durch festen Fremdblock
                break;
            }
        }

        if (validStructure) {
            this.tentApproach = tentPos;
            this.tentInside = getTentInside(tentPos);
            this.criticalRoofBlock = this.tentInside.above();

            this.state = this.blocksToBuild.isEmpty() ? State.STANDBY : State.BUILDING;
            return true;
        }

        return false;
    }

    private boolean checkAndSetTentAt(BlockPos center, BlockPos knownCampfire) {
        Map<BlockPos, BlockState> layout = getTentLayout(center, survivor);

        // 1. Zeltblöcke prüfen: Müssen ersetzbar sein und DÜRFEN KEIN WASSER SEIN
        for (BlockPos worldPos : layout.keySet()) {
            if (!survivor.level().getBlockState(worldPos).canBeReplaced()) {
                return false;
            }
            if (!survivor.level().getFluidState(worldPos).isEmpty()) {
                return false; // Kein Zelt im Wasser bauen!
            }
            if (tooCloseToCampfire(worldPos, knownCampfire)) {
                return false;
            }
        }

        // 2. Einstiegsplatz prüfen (center = tentApproach)
        if (!survivor.level().getFluidState(center).isEmpty()) {
            return false; // Einstieg darf nicht unter Wasser sein
        }
        BlockPos floor = center.below();
        if (!survivor.level().getBlockState(floor).isFaceSturdy(survivor.level(), floor, Direction.UP)) {
            return false;
        }

        // 3. Laufweg ins Zeltinnere prüfen
        Direction dir = getDeterministicDirection(center);
        for (int i = 1; i <= 2; i++) {
            BlockPos pathPos = center.relative(dir, i);
            if (!survivor.level().getBlockState(pathPos).canBeReplaced()) return false;
            if (!survivor.level().getFluidState(pathPos).isEmpty()) return false;
            BlockPos pathFloor = pathPos.below();
            if (!survivor.level().getBlockState(pathFloor).isFaceSturdy(survivor.level(), pathFloor, Direction.UP)) {
                return false;
            }
        }

        this.tentApproach = center;
        this.tentInside = getTentInside(center);
        this.criticalRoofBlock = this.tentInside.above();

        this.blocksToBuild.clear();
        this.blocksToBuild.addAll(layout.keySet());
        this.state = State.BUILDING;
        return true;
    }

    private boolean tooCloseToCampfire(BlockPos pos, BlockPos knownCampfire) {
        if (knownCampfire == null) return false;
        return pos.equals(knownCampfire) || pos.equals(knownCampfire.above());
    }

    private void dismantleOldTent(BlockPos oldTentPos) {
        if (oldTentPos == null) return;
        Map<BlockPos, BlockState> layout = getTentLayout(oldTentPos, survivor);

        for (BlockPos worldPos : layout.keySet()) {
            if (isWoolBlock(survivor.level().getBlockState(worldPos).getBlock())) {
                survivor.level().destroyBlock(worldPos, true, survivor);
            }
        }
    }

    private boolean isWoolBlock(Block block) {
        return block.defaultBlockState().is(BlockTags.WOOL);
    }

    @Override
    public boolean canContinueToUse() {
        boolean cont = switch (state) {
            case BUILDING, ENTERING -> true;
            case SLEEPING -> survivor.getActivity() == SurvivorActivity.SLEEPING
                    && hiddenSafetyTimer < MAX_HIDDEN_TICKS
                    && !tentWasDestroyed;
            default -> false;
        };
        if (!cont && state == State.SLEEPING) {
            net.kb150.survivorcolonies.SurvivorColonies.LOGGER.info("[TENT-GOAL] SLEEPING CANCELLED! Activity: {}, HiddenTimer: {}, Destroyed: {}", 
                survivor.getActivity(), hiddenSafetyTimer, tentWasDestroyed);
        }
        return cont;
    }

    @Override
    public void tick() {
        switch (state) {
            case BUILDING -> {
                if (tentApproach != null) {
                    double dx = survivor.getX() - (tentApproach.getX() + 0.5D);
                    double dz = survivor.getZ() - (tentApproach.getZ() + 0.5D);
                    if (dx * dx + dz * dz > 2.0D) {
                        survivor.getNavigation().moveTo(tentApproach.getX() + 0.5D, tentApproach.getY(), tentApproach.getZ() + 0.5D, 1.0D);
                        return;
                    }
                }

                survivor.getNavigation().stop();
                actionTimer++;
                if (actionTimer >= 6) {
                    actionTimer = 0;
                    if (!blocksToBuild.isEmpty()) {
                        BlockPos targetPos = blocksToBuild.remove(0);

                        if (targetPos.equals(survivor.blockPosition()) || targetPos.equals(survivor.blockPosition().above())) {
                            if (tentApproach != null) {
                                survivor.setPos(tentApproach.getX() + 0.5D, tentApproach.getY(), tentApproach.getZ() + 0.5D);
                            }
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
                        state = (survivor.getActivity() == SurvivorActivity.SLEEPING)
                                ? State.ENTERING
                                : State.STANDBY;
                        actionTimer = 0;
                    }
                }
            }
            case STANDBY -> {}
            case ENTERING -> {
                // Läuft direkt zum Einstiegsblock vor dem Zelt (tentApproach)
                survivor.getNavigation().moveTo(tentApproach.getX() + 0.5D, tentApproach.getY(), tentApproach.getZ() + 0.5D, 1.0D);

                double dx = survivor.getX() - (tentApproach.getX() + 0.5D);
                double dz = survivor.getZ() - (tentApproach.getZ() + 0.5D);

                if (dx * dx + dz * dz < 1.0D) {
                    survivor.getNavigation().stop();
                    // Teleportiert ins Zeltinnere
                    survivor.setPos(tentInside.getX() + 0.5D, tentInside.getY(), tentInside.getZ() + 0.5D);
                    survivor.setHiddenInTent(true);
                    hiddenSafetyTimer = 0;
                    sleepParticleTimer = 0;
                    state = State.SLEEPING;
                }
            }
            case SLEEPING -> {
                if (tentInside != null) {
                    survivor.setPos(tentInside.getX() + 0.5D, tentInside.getY(), tentInside.getZ() + 0.5D);
                }

                hiddenSafetyTimer++;
                sleepParticleTimer++;
                if (sleepParticleTimer >= SLEEP_PARTICLE_INTERVAL) {
                    sleepParticleTimer = 0;
                    survivor.spawnSleepParticles(tentInside);
                }

                if (!isTentIntact()) {
                    tentWasDestroyed = true;
                }
            }
            case IDLE -> {}
        }
    }

    @Override
    public void stop() {
        net.kb150.survivorcolonies.SurvivorColonies.LOGGER.info("[TENT-GOAL] STOP called! State was: {}", this.state);
        survivor.getNavigation().stop();

        if (state == State.SLEEPING) {
            if (tentApproach != null) {
                // Ausstieg: Teleportiert exakt zurück auf den gesicherten Außenplatz
                survivor.setPos(tentApproach.getX() + 0.5D, tentApproach.getY(), tentApproach.getZ() + 0.5D);
            }
            survivor.setHiddenInTent(false);
        }

        if (state == State.ENTERING || state == State.SLEEPING) {
            if (tentWasDestroyed) {
                notifyDestroyed();
            } else {
                state = State.STANDBY;
            }
        }
    }

    public boolean isSleeping() {
        return this.state == State.SLEEPING;
    }

    public boolean isTentIntact() {
        if (criticalRoofBlock == null) return false;
        if (!survivor.level().hasChunkAt(criticalRoofBlock)) return true;
        return survivor.level().getBlockState(criticalRoofBlock).is(BlockTags.WOOL);
    }

    public void notifyDestroyed() {
        this.state = State.IDLE;
        this.blocksToBuild.clear();
        this.tentInside = null;
        this.tentApproach = null;
        this.criticalRoofBlock = null;
        this.tentWasDestroyed = false;
    }

    public BlockPos getTentAnchorPos() {
        return (this.state == State.IDLE) ? null : this.tentInside;
    }

    public BlockPos getTentApproachPos() {
        return (this.state == State.IDLE) ? null : this.tentApproach;
    }

    public String getDebugState() {
        return this.state.name();
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

    public BlockPos getTentInside(BlockPos tentPos) {
        Direction dir = getDeterministicDirection(tentPos);
        return tentPos.relative(dir, 2);
    }

    public Map<BlockPos, BlockState> getTentLayout(BlockPos tentPos, SurvivorEntity survivor) {
        Map<BlockPos, BlockState> layout = new HashMap<>();

        BlockState woolState = getWoolColorForUUID(survivor.getUUID()).defaultBlockState();
        Direction dir = getDeterministicDirection(tentPos);

        Direction left = dir.getCounterClockWise();
        Direction right = dir.getClockWise();

        // 3 Segmente nach vorne (tentPos selbst bleibt als Einstieg frei)
        for (int i = 1; i <= 3; i++) {
            BlockPos forwardPos = tentPos.relative(dir, i);

            layout.put(forwardPos.relative(left), woolState);
            layout.put(forwardPos.relative(right), woolState);
            layout.put(forwardPos.above(), woolState);
        }

        // Rückwand bei Tiefe 3
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
        int index = Math.abs(uuid.hashCode()) % wools.length;
        return wools[index];
    }
}