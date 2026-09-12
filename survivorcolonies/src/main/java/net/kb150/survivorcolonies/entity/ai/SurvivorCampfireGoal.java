package net.kb150.survivorcolonies.entity.ai;

import com.minecolonies.core.entity.other.SittingEntity;
import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;

import java.util.EnumSet;
import java.util.Set;

public class SurvivorCampfireGoal extends Goal {
    private final SurvivorEntity survivor;
    private BlockPos campfirePos;
    private BlockPos sitTargetPos; 
    private boolean isGoalRunning = false;
    private int cookCooldown = 0;
    private int sitLatchTicks = 0;

    public SurvivorCampfireGoal(SurvivorEntity survivor) {
        this.survivor = survivor;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!this.survivor.level().isNight() 
                || this.survivor.getTarget() != null 
                || this.survivor.hurtTime > 0) {
            return false;
        }

        // 1. Check: Hat sie bereits ein Feuer im Gedächtnis?
        BlockPos memoryPos = this.survivor.getKnownCampfirePos();
        if (memoryPos != null) {
            // Prüfen, ob an der gemerkten Stelle noch ein Feuer existiert
            if (this.survivor.level().getBlockState(memoryPos).is(Blocks.CAMPFIRE)) {
                this.campfirePos = memoryPos.immutable();
                this.sitTargetPos = findSafeAdjacentPos(this.campfirePos);
                if (this.sitTargetPos != null) {
                    return true;
                }
            } else {
                // Feuer wurde vom Spieler abgebaut -> Vergessen!
                this.survivor.setKnownCampfirePos(null);
            }
        }

        BlockPos current = this.survivor.blockPosition();

        // 2. Suche im Umkreis (falls sie z.B. das Feuer eines anderen NPCs mitnutzen kann)
        for (BlockPos pos : BlockPos.betweenClosed(current.offset(-12, -3, -12), current.offset(12, 3, 12))) {
            if (this.survivor.level().getBlockState(pos).is(Blocks.CAMPFIRE)) {
                this.campfirePos = pos.immutable();
                this.sitTargetPos = findSafeAdjacentPos(this.campfirePos);
                
                if (this.sitTargetPos != null) {
                    this.survivor.setKnownCampfirePos(this.campfirePos); // Für die Zukunft merken!
                    return true;
                }
            }
        }

        // 3. Kein Feuer da -> Neues bauen!
        BlockPos buildPos = current.relative(this.survivor.getDirection());
        
        if (this.survivor.level().getBlockState(buildPos.below()).isSolid() 
                && this.survivor.level().getBlockState(buildPos).getCollisionShape(this.survivor.level(), buildPos).isEmpty()) {
            
            this.campfirePos = buildPos.immutable();
            this.sitTargetPos = current.immutable(); 
            this.survivor.setKnownCampfirePos(this.campfirePos); // Neues Lagerfeuer im Gedächtnis speichern
            return true;
        }

        return false;
    }

    @Override
    public void start() {
        this.isGoalRunning = true;
        this.sitLatchTicks = 0;
        
        if (this.campfirePos != null && !this.survivor.level().isClientSide 
                && !this.survivor.level().getBlockState(this.campfirePos).is(Blocks.CAMPFIRE)) {
            this.survivor.level().setBlockAndUpdate(this.campfirePos, Blocks.CAMPFIRE.defaultBlockState());
            this.survivor.playSound(SoundEvents.WOOD_PLACE, 1.0F, 1.0F);
        }
    }

    @Override
    public void tick() {
        if (this.campfirePos == null || this.sitTargetPos == null) return;

        double distanceSqToSitPos = this.survivor.distanceToSqr(
            this.sitTargetPos.getX() + 0.5D, 
            this.sitTargetPos.getY(), 
            this.sitTargetPos.getZ() + 0.5D
        );

        this.survivor.getLookControl().setLookAt(
            this.campfirePos.getX() + 0.5D, 
            this.campfirePos.getY() + 0.5D, 
            this.campfirePos.getZ() + 0.5D
        );

        if (!this.survivor.isPassenger() && !this.survivor.level().isClientSide) {
            if (distanceSqToSitPos <= 1.5D) {
                this.survivor.getNavigation().stop();
                this.survivor.setPos(this.sitTargetPos.getX() + 0.5D, this.survivor.getY(), this.sitTargetPos.getZ() + 0.5D);

                if (SittingEntity.sitDown(this.sitTargetPos, this.survivor, 12000)) {
                    this.sitLatchTicks = 20;
                }
            } else {
                // Falls das Feuer weiter weg ist, marschiert sie gezielt dorthin
                this.survivor.getNavigation().moveTo(
                    this.sitTargetPos.getX() + 0.5D, 
                    this.sitTargetPos.getY(), 
                    this.sitTargetPos.getZ() + 0.5D, 
                    1.0D
                );
            }
        }

        if (this.sitLatchTicks > 0) {
            this.sitLatchTicks--;
        }

        if (this.survivor.isPassenger() && !this.survivor.level().isClientSide) {
            if (this.cookCooldown > 0) {
                this.cookCooldown--;
            } else if (this.survivor.getInventory().hasAnyOf(Set.of(Items.ROTTEN_FLESH))) {
                if (this.survivor.level().getBlockEntity(this.campfirePos) instanceof CampfireBlockEntity campfire) {
                    ItemStack fleshStack = new ItemStack(Items.ROTTEN_FLESH, 1);
                    
                    if (campfire.placeFood(this.survivor, fleshStack, 600)) {
                        this.survivor.getInventory().removeItemType(Items.ROTTEN_FLESH, 1);
                        this.survivor.swing(InteractionHand.MAIN_HAND);
                        this.cookCooldown = 100;
                    }
                }
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        boolean isSafeCondition = this.survivor.level().isNight() 
            && this.survivor.getTarget() == null 
            && this.survivor.hurtTime == 0 
            && this.campfirePos != null;

        return isSafeCondition || this.sitLatchTicks > 0;
    }

    @Override
    public void stop() {
        this.isGoalRunning = false;
        this.sitLatchTicks = 0;
        if (this.survivor.isPassenger()) {
            this.survivor.stopRiding();
        }
        this.campfirePos = null;
        this.sitTargetPos = null;
    }

    public boolean isRunning() {
        return this.isGoalRunning;
    }

	private BlockPos findSafeAdjacentPos(BlockPos firePos) {
		// Richtungen in eine Liste packen und mischen, damit nicht jeder den gleichen Platz (z. B. Norden) wählt
		java.util.List<Direction> directions = new java.util.ArrayList<>();
		for (Direction d : Direction.Plane.HORIZONTAL) {
			directions.add(d);
		}
		java.util.Collections.shuffle(directions);

		for (Direction dir : directions) {
			BlockPos adj = firePos.relative(dir);
			
			boolean canStand = this.survivor.level().getBlockState(adj).getCollisionShape(this.survivor.level(), adj).isEmpty();
			boolean isSolidGround = this.survivor.level().getBlockState(adj.below()).isSolid();
			
			if (canStand && isSolidGround) {
				// Prüfen, ob der Platz bereits von einem anderen Überlebenden besetzt ist
				net.minecraft.world.phys.AABB checkBox = new net.minecraft.world.phys.AABB(adj);
				java.util.List<SurvivorEntity> others = this.survivor.level().getEntitiesOfClass(SurvivorEntity.class, checkBox);
				
				boolean isOccupied = false;
				for (SurvivorEntity other : others) {
					if (other != this.survivor) {
						isOccupied = true;
						break;
					}
				}
				
				// Wenn der Platz leer ist, nehmen wir ihn
				if (!isOccupied) {
					return adj.immutable();
				}
			}
		}
		
		// Fallback, falls alle 4 Plätze um das Feuer besetzt oder blockiert sind
		BlockPos forced = firePos.south();
		if (!this.survivor.level().isClientSide) {
			this.survivor.level().destroyBlock(forced, true); 
		}
		return forced.immutable();
	}
}