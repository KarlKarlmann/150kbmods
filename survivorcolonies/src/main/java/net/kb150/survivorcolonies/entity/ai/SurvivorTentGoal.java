package net.kb150.survivorcolonies.entity.ai;

import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public class SurvivorTentGoal extends Goal {
    private final SurvivorEntity survivor;
    private final List<BlockPos> builtTentBlocks = new ArrayList<>();
    private final List<BlockPos> blocksToBuild = new ArrayList<>();
    
    private BlockPos tentEntrance = null;
    private Direction tentDirection = null;
    private BlockPos tentInside = null; // Hier ist die Zelt-Position gespeichert
    private State state = State.IDLE;
    private int actionTimer = 0;

    private enum State {
        IDLE, BUILDING, ENTERING, SLEEPING, DISMANTLING
    }

    public SurvivorTentGoal(SurvivorEntity survivor) {
        this.survivor = survivor;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    private boolean isLowHealth() {
        // Schwelle bei 40% Gesundheit
        return survivor.getHealth() <= (survivor.getMaxHealth() * 0.4F);
    }

    @Override
    public boolean canUse() {
        if (survivor.isPassenger()) return false;
        
        boolean lowHealth = isLowHealth();

        // Zeltaufbau nur nachts, AUSSER er hat sehr wenig Leben
        if (!survivor.level().isNight() && !lowHealth) {
            return false;
        }

        // Bei Feindkontakt nur aufbauen/fliehen, wenn er flieht (low health)
        if (survivor.getTarget() != null && !lowHealth) {
            return false;
        }
        
        int chance = lowHealth ? 5 : 30;
        if (state != State.IDLE || survivor.getRandom().nextInt(chance) != 0) {
            return false;
        }
        
        // Prüft, ob ein passender Platz am Lagerfeuer existiert
        return findTentLocation();
    }
    
    private boolean findTentLocation() {
        BlockPos center = survivor.getKnownCampfirePos();
        if (center == null) {
            center = survivor.blockPosition(); 
        }
        
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                for (int y = -2; y <= 2; y++) {
                    BlockPos pos = center.offset(x, y, z);
                    
                    for (Direction dir : Direction.Plane.HORIZONTAL) {
                        if (checkTentSpace(pos, dir)) {
                            this.tentEntrance = pos;
                            this.tentDirection = dir;
                            return true;
                        }
                    }
                }
            }
        }
        return false; 
    }
    
    private boolean checkTentSpace(BlockPos start, Direction dir) {
        Direction left = dir.getCounterClockWise();
        Direction right = dir.getClockWise();
        
        for (int i = 0; i < 3; i++) {
            BlockPos fwd = start.relative(dir, i);
            
            if (!survivor.level().getBlockState(fwd).canBeReplaced()) return false;
            if (!survivor.level().getBlockState(fwd.below()).isSolid()) return false;
            if (!survivor.level().getBlockState(fwd.above()).canBeReplaced()) return false;
            
            if (!survivor.level().getBlockState(fwd.relative(left)).canBeReplaced()) return false;
            if (!survivor.level().getBlockState(fwd.relative(left).below()).isSolid()) return false;
            
            if (!survivor.level().getBlockState(fwd.relative(right)).canBeReplaced()) return false;
            if (!survivor.level().getBlockState(fwd.relative(right).below()).isSolid()) return false;
        }
        return true;
    }

    @Override
    public void start() {
        this.tentInside = this.tentEntrance.relative(this.tentDirection); 
        
        this.blocksToBuild.clear();
        this.builtTentBlocks.clear();
        
        Direction left = this.tentDirection.getCounterClockWise();
        Direction right = this.tentDirection.getClockWise();

        for (int i = 0; i < 3; i++) {
            BlockPos forwardPos = this.tentEntrance.relative(this.tentDirection, i);
            blocksToBuild.add(forwardPos.relative(left)); 
            blocksToBuild.add(forwardPos.relative(right));
            blocksToBuild.add(forwardPos.above());        
        }
        blocksToBuild.add(this.tentEntrance.relative(this.tentDirection, 2));

        this.state = State.BUILDING;
        this.actionTimer = 0;
        
        // Läuft während des Bauens schon mal in Richtung des Eingangs
        survivor.getNavigation().moveTo(tentEntrance.getX() + 0.5D, tentEntrance.getY(), tentEntrance.getZ() + 0.5D, 1.2D);
    }

    @Override
    public void tick() {
        switch (state) {
            case BUILDING -> {
                actionTimer++;
                int buildSpeed = isLowHealth() ? 2 : 6;
                
                if (actionTimer >= buildSpeed) { 
                    actionTimer = 0;
                    if (!blocksToBuild.isEmpty()) {
                        BlockPos targetPos = blocksToBuild.remove(0);
                        if (survivor.level().getBlockState(targetPos).canBeReplaced()) {
                            Block woolBlock = getWoolColorForUUID(survivor.getUUID());
                            survivor.level().setBlockAndUpdate(targetPos, woolBlock.defaultBlockState());
                            builtTentBlocks.add(targetPos);
                            
                            survivor.swing(InteractionHand.MAIN_HAND);
                            survivor.level().playSound(null, targetPos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                            survivor.getLookControl().setLookAt(targetPos.getX() + 0.5D, targetPos.getY() + 0.5D, targetPos.getZ() + 0.5D);
                        }
                    } else {
                        // Zelt ist fertig, jetzt muss er hinrennen!
                        state = State.ENTERING;
                    }
                }
            }
            case ENTERING -> {
                // Rennt zum aufgebauten Zelt (schneller, wenn er flieht)
                double speed = isLowHealth() ? 1.4D : 1.0D;
                survivor.getNavigation().moveTo(tentInside.getX() + 0.5D, tentInside.getY(), tentInside.getZ() + 0.5D, speed);
                
                // Sobald er auf 2 Blöcke ran ist (2^2 = 4.0D) -> rein teleportieren!
                if (survivor.distanceToSqr(tentInside.getX() + 0.5D, tentInside.getY(), tentInside.getZ() + 0.5D) <= 4.0D) {
                    survivor.getNavigation().stop();
                    survivor.setPos(tentInside.getX() + 0.5D, tentInside.getY(), tentInside.getZ() + 0.5D);
                    survivor.startSleeping(tentInside); // Legt den Überlebenden optisch hin
                    state = State.SLEEPING;
                }
            }
            case SLEEPING -> {
                survivor.getNavigation().stop();
                
                // Wacht auf und baut ab, sobald es Tag ist und er erholt ist
                if (survivor.level().isDay() && !survivor.level().isRaining() && !isLowHealth()) {
                    survivor.stopSleeping();
                    state = State.DISMANTLING;
                    actionTimer = 0;
                }
            }
            case DISMANTLING -> {
                actionTimer++;
                if (actionTimer >= 4) {
                    actionTimer = 0;
                    if (!builtTentBlocks.isEmpty()) {
                        BlockPos targetPos = builtTentBlocks.remove(builtTentBlocks.size() - 1);
                        Block woolBlock = getWoolColorForUUID(survivor.getUUID());
                        if (survivor.level().getBlockState(targetPos).is(woolBlock)) {
                            survivor.level().destroyBlock(targetPos, false);
                            survivor.swing(InteractionHand.MAIN_HAND);
                            survivor.level().playSound(null, targetPos, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
                        }
                    } else {
                        stop();
                    }
                }
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (survivor.getTarget() != null && !isLowHealth()) return false; 
        return state != State.IDLE;
    }

    @Override
    public void stop() {
        survivor.stopSleeping();
        Block woolBlock = getWoolColorForUUID(survivor.getUUID());
        for (BlockPos pos : builtTentBlocks) {
            if (survivor.level().getBlockState(pos).is(woolBlock)) {
                survivor.level().destroyBlock(pos, false);
            }
        }
        builtTentBlocks.clear();
        blocksToBuild.clear();
        state = State.IDLE;
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