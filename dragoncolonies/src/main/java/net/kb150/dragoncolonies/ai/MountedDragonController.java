package net.kb150.dragoncolonies.ai;

import com.minecolonies.api.entity.ai.combat.threat.IThreatTableEntity;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.research.util.ResearchConstants;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.entity.ai.combat.CombatUtils;
import com.minecolonies.core.entity.ai.workers.guard.AbstractEntityAIGuard;
import com.minecolonies.core.util.citizenutils.CitizenItemUtils;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.bookofdragons.entity.component.CombatComponent;
import net.magister.bookofdragons.entity.component.ranged.OmniAttackHandler;
import net.magister.bookofdragons.entity.state.GroundStance;
import net.magister.bookofdragons.entity.state.TransportMode;
import net.kb150.dragoncolonies.DragonColonies;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

public class MountedDragonController {

    private long combatTickCounter = 0;
    private String currentAiPhase = "INITIALIZING";

    public String getCurrentAiPhase() {
        return this.currentAiPhase;
    }

    public void handleFlight(AbstractEntityCitizen worker, DragonBase dragon, AbstractBuildingGuards building, long internalTickCounter, BlockPos currentPatrolPoint) {
        boolean doLog = (internalTickCounter % 40 == 0);

        if (dragon.isNoAi()) dragon.setNoAi(false);
        if (dragon.isOrderedToSit()) dragon.setOrderedToSit(false);
        if (dragon.isInSittingPose()) dragon.setInSittingPose(false);
        if (dragon.getStateContext().getGroundStance() != GroundStance.IDLE && dragon.getTransportMode() != TransportMode.AIRBORNE) {
            dragon.setGroundStance(GroundStance.IDLE);
        }

        LivingEntity attackTarget = worker.getTarget();
        IThreatTableEntity threatEntity = (worker instanceof IThreatTableEntity) ? (IThreatTableEntity) worker : null;
        boolean lostTargetThisTick = false;

        if (attackTarget != null) {
            if (!attackTarget.isAlive() || attackTarget.isRemoved() || attackTarget instanceof DragonBase) {
                if (threatEntity != null) threatEntity.getThreatTable().removeCurrentTarget();
                attackTarget = null;
                worker.setTarget(null);
                lostTargetThisTick = true;
            }
        }

        if (attackTarget == null && (lostTargetThisTick || internalTickCounter % 20 == 0) && worker.level() instanceof ServerLevel serverLevel) {
            attackTarget = findSmartTarget(worker, dragon, building, serverLevel);
            if (attackTarget != null) {
                worker.setTarget(attackTarget);
            }
        }

        String previousPhase = this.currentAiPhase;

        if (attackTarget != null) {
            this.currentAiPhase = "COMBAT";
        } else {
            this.currentAiPhase = "PATROL";
        }

        if (!previousPhase.equals(this.currentAiPhase) && !this.currentAiPhase.equals("COMBAT")) {
            this.combatTickCounter = 0;
            dragon.setShouldDoAerialAttack(false);
            dragon.setDoingAerialAttack(false);
            dragon.setTarget(null);
            
            OmniAttackHandler attackHandler = (OmniAttackHandler) dragon.componentRegistry.get(OmniAttackHandler.class);
            if (attackHandler != null && attackHandler.isAttacking()) {
                attackHandler.handlePlayerFiring(false);
            }
        }

        if (this.currentAiPhase.equals("COMBAT")) {
            handleCombatPhase(worker, dragon, attackTarget, doLog, internalTickCounter);
        }
    }

    private LivingEntity findSmartTarget(AbstractEntityCitizen worker, DragonBase dragon, AbstractBuildingGuards buildingGuards, ServerLevel level) {
        if (worker instanceof IThreatTableEntity threatEntity) {
            LivingEntity threat = threatEntity.getThreatTable().getTargetMob();
            if (threat != null && threat.isAlive() && !threat.isRemoved() && !(threat instanceof DragonBase)
                    && hasLineOfSight(level, dragon, threat)) {
                return threat;
            }
        }

        if (buildingGuards == null) return null;

        double visionRadius = buildingGuards.getBonusVision();
        AABB searchBox;

        boolean isFlying = dragon.getTransportMode() == TransportMode.AIRBORNE || !dragon.onGround();

        if (isFlying) {
            BlockPos dragonPos = dragon.blockPosition();
            int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, dragonPos.getX(), dragonPos.getZ());
            double heightAboveGround = Math.max(0, dragon.getY() - surfaceY);

            double downExtent = heightAboveGround + 10.0D;
            double upExtent = 8.0D;

            searchBox = new AABB(
                dragon.getX() - visionRadius, dragon.getY() - downExtent, dragon.getZ() - visionRadius,
                dragon.getX() + visionRadius, dragon.getY() + upExtent, dragon.getZ() + visionRadius
            );
        } else {
            searchBox = dragon.getBoundingBox().inflate(visionRadius, 6.0D, visionRadius);
        }

        List<Mob> candidates = level.getEntitiesOfClass(Mob.class, searchBox, target ->
            target.isAlive() && !(target instanceof DragonBase)
            && AbstractEntityAIGuard.isAttackableTarget(worker, target)
            && target.blockPosition().closerThan(buildingGuards.getPosition(), buildingGuards.getPatrolDistance())
        );

        if (candidates.isEmpty()) return null;
        candidates.sort(Comparator.comparingDouble(dragon::distanceToSqr));

        for (Mob candidate : candidates) {
            if (hasLineOfSight(level, dragon, candidate)) {
                CombatUtils.notifyGuardsOfTarget(worker, candidate, (int) visionRadius);
                if (worker instanceof IThreatTableEntity threatEntity) {
                    threatEntity.getThreatTable().addThreat(candidate, 1);
                }
                return candidate;
            }
        }
        return null;
    }

    private boolean hasLineOfSight(ServerLevel level, DragonBase dragon, LivingEntity target) {
        Vec3 start = dragon.getEyePosition();
        Vec3 end = target.getEyePosition();
        ClipContext context = new ClipContext(
            start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, dragon
        );
        return level.clip(context).getType() == HitResult.Type.MISS;
    }

    private void handleCombatPhase(AbstractEntityCitizen worker, DragonBase dragon, LivingEntity target, boolean doLog, long internalTickCounter) {
        combatTickCounter++;
        double distToTarget = dragon.distanceTo(target);

        if (doLog && DragonColonies.isLogging("COMBAT")) {
            var handler = (OmniAttackHandler) dragon.componentRegistry.get(OmniAttackHandler.class);
            var combat = (CombatComponent) dragon.componentRegistry.get(CombatComponent.class);

            boolean canFly = dragon.canFly();
            boolean canFire = handler != null && handler.canFire();
            int fuel = handler != null ? handler.getCurrentFuel() : -1;
            int cd = handler != null ? handler.getBreathCooldownTicks() : -1;
            boolean needsOk = dragon.getNeedsSystem() == null || dragon.getNeedsSystem().canUseRangedAttack();

            DragonColonies.debug("COMBAT", "[AERIAL-DIAG] CanFly: " + canFly
                    + " | CanFire: " + canFire
                    + " | Fuel: " + fuel
                    + " | BreathCD: " + cd
                    + " | NeedsOK: " + needsOk
                    + " | ShouldAerial: " + dragon.shouldDoAerialAttack()
                    + " | DoingAerial: " + (combat != null && combat.isDoingAerialAttack())
            );
        }

        try {
            if (dragon.getTarget() != target) {
                dragon.setTarget(target);
            }

            if (internalTickCounter % 20 == 0) {
                double shieldResearch = worker.getCitizenColonyHandler().getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(ResearchConstants.SHIELD_USAGE);
                if (shieldResearch > 0.0D && worker.getItemInHand(InteractionHand.OFF_HAND).getItem() != Items.SHIELD) {
                    int shieldSlot = InventoryUtils.findFirstSlotInItemHandlerWith(worker.getInventoryCitizen(), Items.SHIELD);
                    if (shieldSlot != -1) {
                        CitizenItemUtils.setHeldItem(worker, InteractionHand.OFF_HAND, shieldSlot);
                    }
                }
            }

            if (dragon.getLookControl() != null) dragon.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (worker.getLookControl() != null) worker.getLookControl().setLookAt(target, 30.0F, 30.0F);

            if (distToTarget <= 6.0D && combatTickCounter % 15 == 0) {
                worker.swing(InteractionHand.MAIN_HAND);
            }

            if (combatTickCounter > 600 && distToTarget > 16.0D) {
                worker.setTarget(null);
                dragon.setTarget(null);
                combatTickCounter = 0;
            }

        } catch (Throwable t) {
            DragonColonies.LOGGER.error("[KAMPFPHASE-FEHLER] ", t);
        }
    }
}