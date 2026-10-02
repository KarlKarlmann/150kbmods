package net.kb150.survivorcolonies.entity.ai;

import com.minecolonies.core.entity.other.SittingEntity;
import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Platziert und nutzt ein Lagerfeuer direkt beim Zelt des Überlebenden.
 * Dient als Ort zum Kochen, Regenerieren und Sitzen in der Nacht.
 */
public class SurvivorCampfireGoal extends Goal {
    private final SurvivorEntity survivor;
    private BlockPos campfirePos;
    private BlockPos sitTargetPos; 
    private boolean isGoalRunning = false;
    private int cookCooldown = 0;
    private int sitLatchTicks = 0;

    private int idleSitTicks = 0;
    private static final int MAX_IDLE_SIT_TICKS = 200;

    public SurvivorCampfireGoal(SurvivorEntity survivor) {
        this.survivor = survivor;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.survivor.getTarget() != null || this.survivor.hurtTime > 0) {
            return false;
        }

        boolean hasCampfire = this.survivor.getKnownCampfirePos() != null;
        boolean isNight = this.survivor.level().isNight();
        boolean wantsHeal = wantsToHealAndEat();
        boolean hasFood = hasCookableFood();

        if (hasCampfire && !isNight && !wantsHeal && !hasFood) {
            return false;
        }

        // 1. Bekanntes Lagerfeuer prüfen
        BlockPos memoryPos = this.survivor.getKnownCampfirePos();
        if (memoryPos != null) {
            if (this.survivor.level().getBlockState(memoryPos).is(Blocks.CAMPFIRE)) {
                this.campfirePos = memoryPos.immutable();
                this.sitTargetPos = findSafeAdjacentPos(this.campfirePos);
                if (this.sitTargetPos != null) {
                    net.kb150.survivorcolonies.SurvivorColonies.LOGGER.info("[CAMPFIRE-DEBUG] canUse == TRUE (bekanntes Feuer bei {}, SitPos: {})", this.campfirePos, this.sitTargetPos);
                    return true;
                } else {
                    net.kb150.survivorcolonies.SurvivorColonies.LOGGER.info("[CAMPFIRE-DEBUG] canUse == FALSE: Kein sicherer Sitzplatz um bekanntes Feuer {}", this.campfirePos);
                }
            } else {
                net.kb150.survivorcolonies.SurvivorColonies.LOGGER.info("[CAMPFIRE-DEBUG] Bekanntes Feuer bei {} existiert nicht mehr als Block -> gelöscht", memoryPos);
                this.survivor.setKnownCampfirePos(null);
            }
        }

        BlockPos tentAnchor = getTentAnchorPos();

        // 2. Suche um Zeltanker
        for (BlockPos pos : BlockPos.betweenClosed(tentAnchor.offset(-8, -2, -8), tentAnchor.offset(8, 2, 8))) {
            if (this.survivor.level().getBlockState(pos).is(Blocks.CAMPFIRE)) {
                this.campfirePos = pos.immutable();
                this.sitTargetPos = findSafeAdjacentPos(this.campfirePos);
                if (this.sitTargetPos != null) {
                    this.survivor.setKnownCampfirePos(this.campfirePos);
                    net.kb150.survivorcolonies.SurvivorColonies.LOGGER.info("[CAMPFIRE-DEBUG] canUse == TRUE (Feuer in Umgebung gefunden bei {}, SitPos: {})", this.campfirePos, this.sitTargetPos);
                    return true;
                }
            }
        }

        // 3. Kein Feuer da -> Neues Lagerfeuer bauen
        BlockPos newFirePos = findCampfireSpotNearTent(tentAnchor);
        if (newFirePos != null) {
            this.campfirePos = newFirePos;
            this.sitTargetPos = findSafeAdjacentPos(this.campfirePos);
            if (this.sitTargetPos == null) {
                this.sitTargetPos = tentAnchor;
            }
            this.survivor.setKnownCampfirePos(this.campfirePos);
            net.kb150.survivorcolonies.SurvivorColonies.LOGGER.info("[CAMPFIRE-DEBUG] canUse == TRUE (Neuer Platz gefunden bei {}, SitPos: {})", this.campfirePos, this.sitTargetPos);
            return true;
        }

        return false;
    }

    private BlockPos getTentAnchorPos() {
        SurvivorTentGoal tentGoal = survivor.getTentGoal();
        if (tentGoal != null && tentGoal.getTentApproachPos() != null) {
            return tentGoal.getTentApproachPos();
        }
        if (survivor.getKnownTentPos() != null) {
            return survivor.getKnownTentPos();
        }
        return survivor.blockPosition();
    }

    /**
     * Sucht im Abstand von 2 bis 3 Blöcken um den Zelteingang nach einem ebenen Bauplatz für das Feuer.
     */
    private BlockPos findCampfireSpotNearTent(BlockPos tentApproach) {
        // Sammle alle Blockpositionen des Zeltes, um Überschneidungen auszuschließen
        Set<BlockPos> forbiddenPositions = Collections.emptySet();
        SurvivorTentGoal tentGoal = survivor.getTentGoal();
        if (tentGoal != null) {
            forbiddenPositions = tentGoal.getTentLayout(tentApproach, survivor).keySet();
        }

        // Ermittle alle X/Z-Grundflächen-Koordinaten des Zeltes
        Set<Long> tentFootprintXZ = new java.util.HashSet<>();
        for (BlockPos tentBlock : forbiddenPositions) {
            tentFootprintXZ.add(BlockPos.asLong(tentBlock.getX(), 0, tentBlock.getZ()));
        }
        if (tentGoal != null) {
            BlockPos inside = tentGoal.getTentInside(tentApproach);
            tentFootprintXZ.add(BlockPos.asLong(inside.getX(), 0, inside.getZ()));
        }

        for (BlockPos pos : BlockPos.betweenClosed(tentApproach.offset(-3, -1, -3), tentApproach.offset(3, 0, 3))) {
            // Nicht direkt im Zelteingang bauen
            if (pos.equals(tentApproach)) continue;

            // ABSOLUTES VERBOT: Weder AUF dem Zelt, noch ÜBER dem Zelt, noch IM Zelt!
            // Sobald die X/Z-Koordinate mit IRGENDEINEM Zeltblock übereinstimmt -> SKIPPEN!
            long xzKey = BlockPos.asLong(pos.getX(), 0, pos.getZ());
            if (tentFootprintXZ.contains(xzKey)) {
                continue;
            }

            // Sicherstellen, dass auch der Boden darunter oder die Luft darüber kein Zelt berührt
            if (forbiddenPositions.contains(pos) 
                    || forbiddenPositions.contains(pos.below()) 
                    || forbiddenPositions.contains(pos.above())
                    || forbiddenPositions.contains(pos.below(2))) {
                continue;
            }

            double distSq = pos.distSqr(tentApproach);
            // Idealabstand: 2 bis 3.5 Blöcke vom Eingang entfernt
            if (distSq >= 3.0D && distSq <= 12.0D) {
                var floorState = survivor.level().getBlockState(pos.below());

                boolean canPlace = survivor.level().getBlockState(pos).canBeReplaced()
                        && survivor.level().getFluidState(pos).isEmpty();
                boolean isSolidBelow = floorState.isFaceSturdy(survivor.level(), pos.below(), Direction.UP);
                boolean isAirAbove = survivor.level().getBlockState(pos.above()).isAir();

                if (canPlace && isSolidBelow && isAirAbove) {
                    return pos.immutable();
                }
            }
        }
        return null;
    }

    @Override
    public void start() {
        net.kb150.survivorcolonies.SurvivorColonies.LOGGER.info("[CAMPFIRE-DEBUG] START aufgerufen! Feuer: {}, SitPos: {}", this.campfirePos, this.sitTargetPos);
        this.isGoalRunning = true;
        this.sitLatchTicks = 0;
        this.idleSitTicks = 0;
        
        if (this.campfirePos != null && !this.survivor.level().isClientSide 
                && !this.survivor.level().getBlockState(this.campfirePos).is(Blocks.CAMPFIRE)) {
            this.survivor.level().setBlockAndUpdate(this.campfirePos, Blocks.CAMPFIRE.defaultBlockState());
            this.survivor.playSound(SoundEvents.WOOD_PLACE, 1.0F, 1.0F);
            net.kb150.survivorcolonies.SurvivorColonies.LOGGER.info("[CAMPFIRE-DEBUG] Neuer Campfire-Block platziert bei {}", this.campfirePos);
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

                boolean satDown = SittingEntity.sitDown(this.sitTargetPos, this.survivor, 12000);
                net.kb150.survivorcolonies.SurvivorColonies.LOGGER.info("[CAMPFIRE-DEBUG] SittingEntity.sitDown() aufgerufen bei {}: Erfolg = {}", this.sitTargetPos, satDown);
                if (satDown) {
                    this.sitLatchTicks = 20;
                }
            } else {
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
                        net.kb150.survivorcolonies.SurvivorColonies.LOGGER.info("[CAMPFIRE-DEBUG] Rotten Flesh auf das Feuer gelegt!");
                    }
                }
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        boolean isSafeCondition = this.survivor.getTarget() == null 
            && this.survivor.hurtTime == 0 
            && this.campfirePos != null;

        if (!isSafeCondition) {
            net.kb150.survivorcolonies.SurvivorColonies.LOGGER.info("[CAMPFIRE-DEBUG] canContinueToUse == FALSE (Gefahr/Target/Feuer null). sitLatch: {}", this.sitLatchTicks);
            return this.sitLatchTicks > 0;
        }

        if (hasCookableFood() || wantsToHealAndEat()) {
            this.idleSitTicks = 0;
            return true;
        }

        this.idleSitTicks++;
        boolean continueSitting = this.idleSitTicks < MAX_IDLE_SIT_TICKS || this.sitLatchTicks > 0;
        if (!continueSitting) {
            net.kb150.survivorcolonies.SurvivorColonies.LOGGER.info("[CAMPFIRE-DEBUG] canContinueToUse == FALSE (idleSitTicks abgelaufen: {}/{})", this.idleSitTicks, MAX_IDLE_SIT_TICKS);
        }
        return continueSitting;
    }

    private boolean hasCookableFood() {
        return this.survivor.getInventory().hasAnyOf(Set.of(Items.ROTTEN_FLESH));
    }

    private boolean wantsToHealAndEat() {
        return this.survivor.getHealth() < this.survivor.getMaxHealth()
                && this.survivor.hasEdibleFoodInInventory();
    }

    @Override
    public void stop() {
        net.kb150.survivorcolonies.SurvivorColonies.LOGGER.info("[CAMPFIRE-DEBUG] STOP aufgerufen. War Passenger: {}", this.survivor.isPassenger());
        this.isGoalRunning = false;
        this.sitLatchTicks = 0;
        this.idleSitTicks = 0;
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
        List<Direction> directions = new ArrayList<>();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            directions.add(d);
        }
        Collections.shuffle(directions);

        for (Direction dir : directions) {
            BlockPos adj = firePos.relative(dir);
            
            boolean canStand = this.survivor.level().getBlockState(adj).getCollisionShape(this.survivor.level(), adj).isEmpty();
            boolean isSolidGround = this.survivor.level().getBlockState(adj.below()).isSolid();
            
            if (canStand && isSolidGround) {
                net.minecraft.world.phys.AABB checkBox = new net.minecraft.world.phys.AABB(adj);
                List<SurvivorEntity> others = this.survivor.level().getEntitiesOfClass(SurvivorEntity.class, checkBox);
                
                boolean isOccupied = false;
                for (SurvivorEntity other : others) {
                    if (other != this.survivor) {
                        isOccupied = true;
                        break;
                    }
                }
                
                if (!isOccupied) {
                    return adj.immutable();
                }
            }
        }
        
        BlockPos forced = firePos.south();
        if (!this.survivor.level().isClientSide) {
            this.survivor.level().destroyBlock(forced, true); 
        }
        return forced.immutable();
    }
}