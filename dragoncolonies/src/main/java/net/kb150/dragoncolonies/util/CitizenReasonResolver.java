package net.kb150.dragoncolonies.util;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.IGuardBuilding;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.entity.ai.ITickingStateAI;
import com.minecolonies.api.entity.ai.combat.threat.IThreatTableEntity;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import com.minecolonies.core.entity.ai.workers.guard.AbstractEntityAIGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Löst den semantischen Grund für die Bewegung oder das Verhalten eines Bürgers auf.
 */
public final class CitizenReasonResolver {

    private CitizenReasonResolver() {}

    public record ReasonInfo(
            String mainReason,
            String detail,
            BlockPos targetPos,
            String jobName,
            String aiState
    ) {
        @Override
        public String toString() {
            return String.format("[%s | Detail: %s | TargetPos: %s | Job: %s | State: %s]",
                    mainReason,
                    detail,
                    targetPos != null ? targetPos.toShortString() : "None",
                    jobName,
                    aiState);
        }
    }

    public static ReasonInfo resolveReason(AbstractEntityCitizen citizen) {
        if (citizen == null) {
            return new ReasonInfo("UNKNOWN", "Citizen ist null", null, "None", "None");
        }

        ICitizenData data = citizen.getCitizenData();
        IJob<?> job = data != null ? data.getJob() : null;
        String jobName = job != null ? job.getJobRegistryEntry().getKey().toString() : "Unemployed";
        
        ITickingStateAI tickingAI = citizen.getCitizenJobHandler().getWorkAI();
        String aiStateName = tickingAI != null && tickingAI.getState() != null 
                ? tickingAI.getState().toString() 
                : "NO_AI_STATE";

        // 1. Primäre Bedürfnisse & Überlebensinstinkte
        if (data != null && data.isAsleep()) {
            return new ReasonInfo("SLEEPING", "Bürger schläft im Bett", data.getBedPos(), jobName, aiStateName);
        }

        VisibleCitizenStatus status = data != null ? data.getStatus() : null;
        if (status != null) {
            if (status == VisibleCitizenStatus.SLEEP) {
                return new ReasonInfo("GOING_TO_BED", "Sucht Bett auf", data.getBedPos(), jobName, aiStateName);
            }
            if (status == VisibleCitizenStatus.EAT) {
                return new ReasonInfo("HUNGRY_EATING", "Sucht Nahrung / Restaurant", data.getStatusPosition(), jobName, aiStateName);
            }
            if (status == VisibleCitizenStatus.RAIDED) {
                return new ReasonInfo("FLEEING_RAID", "Flucht vor Kolonie-Raid", citizen.blockPosition(), jobName, aiStateName);
            }
            if (status == VisibleCitizenStatus.MOURNING) {
                return new ReasonInfo("MOURNING", "Trauert um verstorbenen Bürger", data.getStatusPosition(), jobName, aiStateName);
            }
            if (status == VisibleCitizenStatus.SICK) {
                return new ReasonInfo("SICK", "Bürger ist krank", data.getStatusPosition(), jobName, aiStateName);
            }
            if (status == VisibleCitizenStatus.HOUSE) {
                return new ReasonInfo("LEISURE_HOME", "Freizeit / Feierabend / Auf dem Heimweg", data.getHomePosition(), jobName, aiStateName);
            }
        }

        // 2. Spezialfall: Wachen (Guards / Knights / Rangers)
        if (tickingAI instanceof AbstractEntityAIGuard<?, ?> guardAI) {
            return resolveGuardReason(citizen, guardAI, jobName, aiStateName);
        }

        // 3. Zivile Arbeiter (Worker AI mit State Machine)
        if (tickingAI instanceof AbstractEntityAIBasic<?, ?> workerAI) {
            return resolveWorkerReason(citizen, workerAI, jobName, aiStateName);
        }

        // 4. Vanilla / MineColonies Kampfziel
        LivingEntity vanillaTarget = citizen.getTarget();
        if (vanillaTarget != null && vanillaTarget.isAlive()) {
            return new ReasonInfo("COMBAT_TARGET", "Verfolgt Angreifer: " + vanillaTarget.getName().getString(),
                    vanillaTarget.blockPosition(), jobName, aiStateName);
        }

        return new ReasonInfo("GENERIC_WORK", "Arbeitet regulär oder navigiert frei", 
                data != null ? data.getStatusPosition() : null, jobName, aiStateName);
    }

    private static ReasonInfo resolveGuardReason(AbstractEntityCitizen citizen,
                                                 AbstractEntityAIGuard<?, ?> guardAI,
                                                 String jobName,
                                                 String aiStateName) {
        // Kampfzustand
        LivingEntity target = citizen.getTarget();
        if (citizen instanceof IThreatTableEntity threatEntity && threatEntity.getThreatTable().getTargetMob() != null) {
            target = threatEntity.getThreatTable().getTargetMob();
        }
        if (target != null && target.isAlive()) {
            return new ReasonInfo("GUARD_COMBAT", "Bekämpft Mob: " + target.getName().getString(),
                    target.blockPosition(), jobName, aiStateName);
        }

        IBuilding workBuilding = citizen.getCitizenData() != null ? citizen.getCitizenData().getWorkBuilding() : null;
        if (workBuilding instanceof IGuardBuilding guardBuilding) {
            // Rally durch Spieler-Banner
            if (guardBuilding.getRallyLocation() != null) {
                BlockPos rallyPos = guardBuilding.getRallyLocation().getInDimensionLocation();
                return new ReasonInfo("GUARD_RALLY", "Folgt Rally-Banner", rallyPos, jobName, aiStateName);
            }

            // Spieler begleiten (Follow Mode)
            Player followPlayer = guardBuilding.getPlayerToFollowOrRally();
            if (followPlayer != null) {
                return new ReasonInfo("GUARD_FOLLOW_PLAYER", "Begleitet Spieler: " + followPlayer.getName().getString(),
                        followPlayer.blockPosition(), jobName, aiStateName);
            }

            String task = guardBuilding.getTask();
            if ("com.minecolonies.core.guard.setting.patrol".equals(task)) {
                BlockPos patrolTarget = guardAI.getCurrentPatrolPoint();
                if (patrolTarget == null) {
                    patrolTarget = guardBuilding.getNextPatrolTarget(false);
                }
                boolean isManual = guardBuilding.shallPatrolManually();
                return new ReasonInfo("GUARD_PATROL", isManual ? "Manuelle Patrouillenroute" : "Automatische Patrouille",
                        patrolTarget, jobName, aiStateName);
            }

            if ("com.minecolonies.core.guard.setting.guard".equals(task)) {
                BlockPos guardPos = guardBuilding.getGuardPos(citizen);
                return new ReasonInfo("GUARD_HOLD_POSITION", "Bewacht festen Posten", guardPos, jobName, aiStateName);
            }

            if ("com.minecolonies.core.guard.setting.patrol_mine".equals(task)) {
                return new ReasonInfo("GUARD_PATROL_MINE", "Sichert Mine ab", guardBuilding.getMinePos(), jobName, aiStateName);
            }
        }

        return new ReasonInfo("GUARD_IDLE", "Wache wartet oder patrouilliert im Umkreis",
                guardAI.getCurrentPatrolPoint(), jobName, aiStateName);
    }

    private static ReasonInfo resolveWorkerReason(AbstractEntityCitizen citizen,
                                                  AbstractEntityAIBasic<?, ?> workerAI,
                                                  String jobName,
                                                  String aiStateName) {
        if ("INVENTORY_FULL".equals(aiStateName)) {
            BlockPos dumpPos = workerAI.building != null ? workerAI.building.getPosition() : null;
            return new ReasonInfo("WORKER_DUMP_INVENTORY", "Inventar voll -> Lager/Gebäude ansteuern", dumpPos, jobName, aiStateName);
        }

        if ("GATHERING_REQUIRED_MATERIALS".equals(aiStateName) || "NEEDS_ITEM".equals(aiStateName)) {
            BlockPos buildingPos = workerAI.building != null ? workerAI.building.getPosition() : null;
            return new ReasonInfo("WORKER_FETCH_SUPPLIES", "Holt Material oder Werkzeug aus Lagerkiste", buildingPos, jobName, aiStateName);
        }

        if ("PAUSED".equals(aiStateName)) {
            return new ReasonInfo("WORKER_PAUSED", "Arbeiter pausiert / streunt herum", null, jobName, aiStateName);
        }

        if ("START_WORKING".equals(aiStateName) || "WORKING".equals(aiStateName)) {
            return new ReasonInfo("WORKER_WORKING", "Auf dem Weg zum Einsatzort / Baustelle", null, jobName, aiStateName);
        }

        return new ReasonInfo("WORKER_TASK", "Worker-Zustand: " + aiStateName,
                workerAI.building != null ? workerAI.building.getPosition() : null, jobName, aiStateName);
    }
}