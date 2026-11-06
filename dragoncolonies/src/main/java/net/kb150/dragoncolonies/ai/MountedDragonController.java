package net.kb150.dragoncolonies.ai;

import com.minecolonies.api.entity.ai.combat.threat.IThreatTableEntity;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.entity.ai.combat.CombatUtils;
import com.minecolonies.core.entity.ai.workers.guard.AbstractEntityAIGuard;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.liberanimalium.body.state.TransportMode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

public class MountedDragonController {

    private String currentAiPhase = "INITIALIZING";

    public String getCurrentAiPhase() {
        return this.currentAiPhase;
    }

    public void handleFlight(AbstractEntityCitizen worker, DragonBase dragon, AbstractBuildingGuards building, long internalTickCounter, BlockPos currentPatrolPoint, boolean isGroundTask) {
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
        this.currentAiPhase = (attackTarget != null) ? "COMBAT" : "PATROL";

        if (!previousPhase.equals(this.currentAiPhase) && !this.currentAiPhase.equals("COMBAT")) {
            dragon.setTarget(null);
        }

        if (this.currentAiPhase.equals("COMBAT")) {
            if (dragon.getTarget() != attackTarget) {
                dragon.setTarget(attackTarget);
            }
            // Dem Drachen vorgaukeln, er waere von dem Zombie attackiert worden,
            // damit FAUNAs Ueberlebensinstinkt (HuntDrive/EnmityDrive) den Kampf voll uebernimmt!
            if (internalTickCounter % 20 == 0 && attackTarget != null) {
                dragon.setLastHurtByMob(attackTarget);
            }
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
}