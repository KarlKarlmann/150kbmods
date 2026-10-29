package net.kb150.dragoncolonies.ai;

import com.minecolonies.api.entity.ai.combat.threat.IThreatTableEntity;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.research.util.ResearchConstants;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.entity.ai.combat.CombatUtils;
import com.minecolonies.core.entity.ai.workers.guard.AbstractEntityAIGuard;
import com.minecolonies.core.util.citizenutils.CitizenItemUtils;
import net.kb150.dragoncolonies.DragonColonies;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.liberanimalium.body.state.TransportMode;
import net.magister.liberanimalium.chassis.component.CommandComponent;
import net.magister.liberanimalium.chassis.component.melee.OmniMeleeAttackHandler;
import net.magister.liberanimalium.chassis.component.ranged.OmniAttackHandler;
import net.magister.liberanimalium.fauna.api.MoveIntent;
import net.magister.liberanimalium.fauna.api.MoveMode;
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

/**
 * Steuert den Drachen im aktiven Luftkampf der Wache:
 * - Haelt den Drachen im Flug (TransportMode.AIRBORNE) auf Kampfhoehe (+10m ueber Feind).
 * - Fliegt dynamische Orbit- & Anflugschleifen um das Ziel.
 * - Feuert Feuerodem via OmniAttackHandler und beisst bei Nahkontakt via OmniMeleeAttackHandler.
 */
public class MountedDragonController {

    private long combatTickCounter = 0;
    private double combatOrbitAngle = 0.0D;
    private String currentAiPhase = "INITIALIZING";

    public String getCurrentAiPhase() {
        return this.currentAiPhase;
    }

    public void handleFlight(
            AbstractEntityCitizen worker,
            DragonBase dragon,
            AbstractBuildingGuards building,
            long internalTickCounter,
            BlockPos currentPatrolPoint,
            boolean isGroundTask
    ) {
        boolean doLog = (internalTickCounter % 40 == 0);

        if (dragon.isNoAi()) dragon.setNoAi(false);
        if (dragon.isOrderedToSit()) dragon.setOrderedToSit(false);
        if (dragon.isInSittingPose()) dragon.setInSittingPose(false);

        // Weckt den Drachen sofort auf, falls FAUNA Rest/Sleep getriggert hat
        CommandComponent cmd = (CommandComponent) dragon.componentRegistry.get(CommandComponent.class);
        if (cmd != null) {
            cmd.wakeFromRest();
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

        // Bei Phasenwechsel weg von Combat: Sauberes Beenden aller Angriffe
        if (!previousPhase.equals(this.currentAiPhase) && !this.currentAiPhase.equals("COMBAT")) {
            this.combatTickCounter = 0;
            dragon.setTarget(null);
            dragon.dropTrigger();

            OmniAttackHandler breath = (OmniAttackHandler) dragon.componentRegistry.get(OmniAttackHandler.class);
            if (breath != null && (breath.isCharging() || breath.isBreathing())) {
                breath.handlePlayerAttack(false);
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

        try {
            // 1. FAUNA VON DER LEINE LASSEN!
            // Wir uebergeben dem Drachen lediglich das Ziel der Wache. 
            // Ab hier uebernimmt FAUNA (FaunaTicker) komplett selbststaendig!
            if (dragon.getTarget() != target) {
                dragon.setTarget(target);
            }

            // 2. BLICK-AUSRICHTUNG der Wache
            if (worker.getLookControl() != null) worker.getLookControl().setLookAt(target, 45.0F, 45.0F);

            double distToTarget = dragon.distanceTo(target);

            // 3. WACHEN-ANGRIFF: Axt-Schwung bei Nahkontakt als Unterstuetzung
            if (distToTarget <= 6.0D && combatTickCounter % 15 == 0) {
                worker.swing(InteractionHand.MAIN_HAND);
            }

            // 4. SCHILD-NUTZUNG der Wache
            if (internalTickCounter % 20 == 0) {
                double shieldResearch = worker.getCitizenColonyHandler().getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(ResearchConstants.SHIELD_USAGE);
                if (shieldResearch > 0.0D && worker.getItemInHand(InteractionHand.OFF_HAND).getItem() != Items.SHIELD) {
                    int shieldSlot = InventoryUtils.findFirstSlotInItemHandlerWith(worker.getInventoryCitizen(), Items.SHIELD);
                    if (shieldSlot != -1) {
                        CitizenItemUtils.setHeldItem(worker, InteractionHand.OFF_HAND, shieldSlot);
                    }
                }
            }

            // 5. TIMEOUT-SCHUTZ
            if (combatTickCounter > 600 && distToTarget > 20.0D) {
                worker.setTarget(null);
                dragon.setTarget(null);
                dragon.dropTrigger();
                combatTickCounter = 0;
            }

        } catch (Throwable t) {
            DragonColonies.LOGGER.error("[KAMPFPHASE-FEHLER] ", t);
        }
    }
}