
package net.kb150.survivorcolonies.entity.ai;

import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.EnumSet;

public class SurvivorHarvestRemainsGoal extends Goal {
    private final SurvivorEntity survivor;
    private BlockPos targetBlockPos;
    private int miningTicks = 0;

    // Standard-Block ID aus deiner zombiesleeping Mod
    private static final ResourceLocation ZOMBIE_REMAINS_ID = new ResourceLocation("zombiesleeping", "zombieremains");

    public SurvivorHarvestRemainsGoal(SurvivorEntity survivor) {
        this.survivor = survivor;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // Nicht im Kampf, nicht beim Reiten/Sitzen und nicht beim Gespräch mit Spielern
        if (this.survivor.isPassenger() 
                || this.survivor.getTarget() != null 
                || this.survivor.getTradingPlayer() != null) {
            return false;
        }

        SurvivorActivity activity = this.survivor.getActivity();
        if (activity == SurvivorActivity.COMBAT || activity == SurvivorActivity.SLEEPING) {
            return false;
        }

        // 5% Chance pro Tick zu suchen, um die Performance zu schonen
        if (this.survivor.getRandom().nextInt(20) != 0) return false;

        BlockPos current = this.survivor.blockPosition();

        // Sucht im Umkreis von 10 Blöcken nach dem Zombieremains-Block
        for (BlockPos pos : BlockPos.betweenClosed(current.offset(-10, -2, -10), current.offset(10, 2, 10))) {
            BlockState state = this.survivor.level().getBlockState(pos);
            
            if (isHarvestableBlock(state.getBlock())) {
                this.targetBlockPos = pos.immutable();
                return true;
            }
        }

        return false;
    }

    @Override
    public void start() {
        if (this.targetBlockPos != null) {
            this.miningTicks = 40; // 2 Sekunden Abbauzeit
            this.survivor.getNavigation().moveTo(
                this.targetBlockPos.getX() + 0.5D, 
                this.targetBlockPos.getY(), 
                this.targetBlockPos.getZ() + 0.5D, 
                1.0D
            );
        }
    }

    @Override
    public void tick() {
        if (this.targetBlockPos == null) return;

        double distanceSq = this.survivor.distanceToSqr(
            this.targetBlockPos.getX() + 0.5D, 
            this.targetBlockPos.getY(), 
            this.targetBlockPos.getZ() + 0.5D
        );

        // Nah genug am Haufen?
        if (distanceSq <= 3.0D) {
            this.survivor.getNavigation().stop();
            this.survivor.getLookControl().setLookAt(
                this.targetBlockPos.getX() + 0.5D, 
                this.targetBlockPos.getY() + 0.5D, 
                this.targetBlockPos.getZ() + 0.5D
            );

            this.miningTicks--;

            // Arm-Schwung & Kies-Sound alle 5 Ticks
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

            // Nach 2 Sekunden: Block zerstören!
            if (this.miningTicks <= 0) {
                if (!this.survivor.level().isClientSide) {
                    // destroyBlock lässt das Loot (Rotten Flesh) gewöhnlich fallen
                    this.survivor.level().destroyBlock(this.targetBlockPos, true, this.survivor);
                }
                this.stop();
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        return this.targetBlockPos != null 
            && this.survivor.getTarget() == null 
            && isHarvestableBlock(this.survivor.level().getBlockState(this.targetBlockPos).getBlock());
    }

    @Override
    public void stop() {
        this.targetBlockPos = null;
        this.miningTicks = 0;
    }

    // Prüft dynamisch, ob der Block abbaubar ist (ohne Absturz, falls zombiesleeping fehlt)
    private boolean isHarvestableBlock(Block block) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        return id != null && id.equals(ZOMBIE_REMAINS_ID);
    }
}

