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

    public void handleFlight(AbstractEntityCitizen worker, DragonBase dragon, AbstractBuildingGuards building, long internalTickCounter, BlockPos currentPatrolPoint, boolean isGroundTask) {
        boolean doLog = (internalTickCounter % 40 == 0);

        // Basis-Status des Drachen sicherstellen
        if (dragon.isNoAi()) dragon.setNoAi(false);
        if (dragon.isOrderedToSit()) dragon.setOrderedToSit(false);
        if (dragon.isInSittingPose()) dragon.setInSittingPose(false);
        if (dragon.getStateContext().getGroundStance() != GroundStance.IDLE && dragon.getTransportMode() != TransportMode.AIRBORNE) {
            dragon.setGroundStance(GroundStance.IDLE);
        }

        LivingEntity attackTarget = worker.getTarget();
        IThreatTableEntity threatEntity = (worker instanceof IThreatTableEntity) ? (IThreatTableEntity) worker : null;
        boolean lostTargetThisTick = false;

        // 1. Target-Validierung (Aktuelles Ziel auf Gültigkeit prüfen)
        if (attackTarget != null) {
            if (!attackTarget.isAlive() || attackTarget.isRemoved() || attackTarget instanceof DragonBase) {
                if (threatEntity != null) threatEntity.getThreatTable().removeCurrentTarget();
                attackTarget = null;
                worker.setTarget(null);
                lostTargetThisTick = true;
            }
        }

        // 2. Zielsuche komplett über findSmartTarget (ThreatTable + intelligentes Radar in einem Schritt)
        if (attackTarget == null && (lostTargetThisTick || internalTickCounter % 20 == 0) && worker.level() instanceof ServerLevel serverLevel) {
            attackTarget = findSmartTarget(worker, dragon, building, serverLevel);
            if (attackTarget != null) {
                worker.setTarget(attackTarget);
            }
        }

        String previousPhase = this.currentAiPhase;

        // Phasen bestimmen
        if (attackTarget != null) {
            this.currentAiPhase = "COMBAT";
        } else if (isGroundTask) {
            this.currentAiPhase = "GROUND_TASK";
        } else {
            this.currentAiPhase = "PATROL";
        }

        // Bei Phasenwechsel zurück aus dem Kampf: BoD-Flags säubern!
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

        // NUR IM KAMPF übernimmt der Controller aktiv Befehle!
        if (this.currentAiPhase.equals("COMBAT")) {
            handleCombatPhase(worker, dragon, attackTarget, doLog, internalTickCounter);
        }
        // PATROL und GROUND_TASK bewegen sich rein über Minecolonies -> Mixin -> DragonNavigationHandler!
    }

private LivingEntity findSmartTarget(AbstractEntityCitizen worker, DragonBase dragon, AbstractBuildingGuards buildingGuards, ServerLevel level) {
    // 1. ThreatTable (Minecolonies Aggro)
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

    // Prüfe, ob der Drache in der Luft ist
    boolean isFlying = dragon.getTransportMode() == TransportMode.AIRBORNE || !dragon.onGround();

    if (isFlying) {
        // FLUG-MODUS: Dynamische Höhen-Erweiterung bis zum Erdboden
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
        // BODEN-MODUS: Schneller, flacher Standard-Scan
        searchBox = dragon.getBoundingBox().inflate(visionRadius, 6.0D, visionRadius);
    }

    List<Mob> candidates = level.getEntitiesOfClass(Mob.class, searchBox, target ->
        target.isAlive() && !(target instanceof DragonBase)
        // isAttackableTarget prüft automatisch die "hostiles"-Liste des Horts & Allianzen!
        && AbstractEntityAIGuard.isAttackableTarget(worker, target)
        // Maximale Einsatzdistanz um das Hort-Gebäude prüfen:
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

        try {
            if (dragon.getTarget() != target) {
                dragon.setTarget(target);
            }

            // Schild ausrüsten (sofern erforscht)
            if (internalTickCounter % 20 == 0) {
                double shieldResearch = worker.getCitizenColonyHandler().getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(ResearchConstants.SHIELD_USAGE);
                if (shieldResearch > 0.0D && worker.getItemInHand(InteractionHand.OFF_HAND).getItem() != Items.SHIELD) {
                    int shieldSlot = InventoryUtils.findFirstSlotInItemHandlerWith(worker.getInventoryCitizen(), Items.SHIELD);
                    if (shieldSlot != -1) {
                        CitizenItemUtils.setHeldItem(worker, InteractionHand.OFF_HAND, shieldSlot);
                    }
                }
            }

            // Blickrichtung von Drache und Wache auf den Gegner fixieren
            if (dragon.getLookControl() != null) dragon.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (worker.getLookControl() != null) worker.getLookControl().setLookAt(target, 30.0F, 30.0F);

            OmniAttackHandler attackHandler = (OmniAttackHandler) dragon.componentRegistry.get(OmniAttackHandler.class);
            CombatComponent combat = (CombatComponent) dragon.componentRegistry.get(CombatComponent.class);
            boolean isDoingAerial = combat != null && combat.isDoingAerialAttack();

            // BoD Luftangriffe auslösen
            if (attackHandler != null && dragon.canFly() && distToTarget > 8.0D) {
                if (!isDoingAerial && !dragon.shouldDoAerialAttack()) {
                    if (combatTickCounter == 5 || combatTickCounter % 80 == 0) {
                        dragon.setShouldDoAerialAttack(true);
                    }
                }
            }

            if (isDoingAerial || dragon.shouldDoAerialAttack()) {
                return;
            }

            // Timeout-Schutz für verbuggte Kämpfe
            if (combatTickCounter > 600 && distToTarget > 12.0D) {
                worker.setTarget(null);
                dragon.setTarget(null);
                combatTickCounter = 0;
                return;
            }

            // Nahkampf-Schlag der Wache
            if (distToTarget <= 6.0D && combatTickCounter % 15 == 0) {
                worker.swing(InteractionHand.MAIN_HAND);
            }

        } catch (Throwable t) {
            System.err.println("!!! [KAMPFPHASE-FEHLER] " + t.getMessage());
        }
    }
}