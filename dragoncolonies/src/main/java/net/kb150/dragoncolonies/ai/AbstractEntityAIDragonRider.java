package net.kb150.dragoncolonies.ai;

import com.minecolonies.api.entity.ai.combat.CombatAIStates;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickingTransition;
import com.minecolonies.api.entity.ai.workers.util.GuardGear;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.entity.ai.workers.guard.AbstractEntityAIGuard;
import net.kb150.dragoncolonies.DragonColonies;
import net.kb150.dragoncolonies.buildings.BuildingDragonRoost;
import net.kb150.dragoncolonies.buildings.modules.DragonStorageModule;
import net.kb150.dragoncolonies.network.DragonColoniesNetwork;
import net.kb150.dragoncolonies.network.message.RiderLeapMessage;
import net.kb150.dragoncolonies.util.DragonStatusHelper;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.bookofdragons.entity.component.ranged.OmniAttackHandler;
import net.magister.bookofdragons.entity.state.GroundStance;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public abstract class AbstractEntityAIDragonRider<J extends AbstractJobGuard<J>, B extends AbstractBuildingGuards> extends AbstractEntityAIGuard<J, B> {

    protected DragonBase assignedDragon = null;
    protected boolean isMountingDragon = false;
    protected boolean isReturningDragon = false;
    protected int retrievedCooldownTicks = 0;
    protected int mountingTicks = 0;
    private static final int MAX_MOUNT_TICKS = 60;
    private Vec3 currentReturnTarget = null;
    private long nextRetrieveAllowedTime = 0;
    private final MountedDragonController flightController = new MountedDragonController();
    private static final java.util.Map<java.util.UUID, AbstractEntityAIDragonRider<?, ?>> RIDERS_BY_CITIZEN = new java.util.concurrent.ConcurrentHashMap<>();
    private static java.lang.reflect.Field SLEEP_TIMER_FIELD;

    private BlockPos lastReachedPatrolPoint = null;
    private long radarTickCounter = 0;
    private int cachedLoiterAltitude = -1;

    static {
        try {
            SLEEP_TIMER_FIELD = AbstractEntityAIGuard.class.getDeclaredField("sleepTimer");
            SLEEP_TIMER_FIELD.setAccessible(true);
        } catch (Exception e) {
            SLEEP_TIMER_FIELD = null;
        }
    }

    public AbstractEntityAIDragonRider(@NotNull J job) {
        super(job);
        this.registerTargets(new TickingTransition[]{
                new AITarget(CombatAIStates.NO_TARGET, this::handleDragonControl, 1),
                new AITarget(CombatAIStates.ATTACKING, this::handleDragonControl, 1),

                new AITarget(AIWorkerState.START_WORKING, this::handleDragonControl, 1),
                new AITarget(AIWorkerState.INVENTORY_FULL, this::handleDragonControl, 1),
                new AITarget(AIWorkerState.DECIDE, this::handleDragonControl, 1),
                new AITarget(AIWorkerState.NEEDS_ITEM, this::handleDragonControl, 1),
                new AITarget(AIWorkerState.GATHERING_REQUIRED_MATERIALS, this::handleDragonControl, 1),
                new AITarget(AIWorkerState.GET_MATERIALS, this::handleDragonControl, 1),

                new AITarget(AIWorkerState.GUARD_DECIDE, this::handleDragonControl, 1),
                new AITarget(AIWorkerState.GUARD_PATROL, this::handleDragonControl, 1),
                new AITarget(AIWorkerState.GUARD_GUARD, this::handleDragonControl, 1),
                new AITarget(AIWorkerState.GUARD_FOLLOW, this::handleDragonControl, 1),
                new AITarget(AIWorkerState.GUARD_ATTACK_PHYSICAL, this::handleDragonControl, 1),
                new AITarget(AIWorkerState.GUARD_ATTACK_RANGED, this::handleDragonControl, 1),
                new AITarget(AIWorkerState.GUARD_ATTACK_PROTECT, this::handleDragonControl, 1)
        });

        for (List<GuardGear> gearLevel : this.itemsNeeded) {
            gearLevel.removeIf(gear -> gear.getType() == EquipmentSlot.CHEST || gear.getType() == EquipmentSlot.MAINHAND);
        }
        this.toolsNeeded.add((EquipmentTypeEntry) ModEquipmentTypes.axe.get());
    }

    public static AbstractEntityAIDragonRider<?, ?> getRiderForCitizen(AbstractEntityCitizen citizen) {
        if (citizen == null || citizen.getCitizenJobHandler() == null) {
            return null;
        }

        com.minecolonies.api.colony.jobs.IJob<?> colonyJob = citizen.getCitizenJobHandler().getColonyJob();
        if (colonyJob != null && colonyJob.getWorkerAI() instanceof AbstractEntityAIDragonRider<?, ?> riderAI) {
            return riderAI;
        }

        return null;
    }

    public UUID getAssignedDragonUUID() {
        if (this.job instanceof net.kb150.dragoncolonies.jobs.JobDragonRider dragonJob) {
            return dragonJob.getAssignedDragonUUID();
        }
        return null;
    }

    public void setAssignedDragonUUID(UUID uuid) {
        if (this.job instanceof net.kb150.dragoncolonies.jobs.JobDragonRider dragonJob) {
            dragonJob.setAssignedDragonUUID(uuid);
        }
    }

    public DragonBase getAssignedDragon() {
        if (this.assignedDragon == null && this.worker != null && !this.worker.level().isClientSide()) {
            UUID savedId = getAssignedDragonUUID();
            if (savedId != null && this.worker.level() instanceof ServerLevel level) {
                Entity entity = level.getEntity(savedId);
                if (entity instanceof DragonBase dragon && dragon.isAlive()) {
                    this.assignedDragon = dragon;
                }
            }
        }
        return this.assignedDragon;
    }

    public boolean isReturningDragon() {
        return this.isReturningDragon;
    }

    public B getBuilding() {
        return this.building;
    }

    public boolean hasAxe() {
        if (this.worker == null) return false;
        int maxLevel = this.building != null ? this.building.getMaxEquipmentLevel() : 1;
        int weaponSlot = InventoryUtils.getFirstSlotOfItemHandlerContainingEquipment(
                this.worker.getInventoryCitizen(),
                (EquipmentTypeEntry) ModEquipmentTypes.axe.get(),
                0,
                maxLevel
        );
        return weaponSlot != -1;
    }

    @Override
    protected int getActionsDoneUntilDumping() {
        return 120 * (this.building != null ? this.building.getBuildingLevelEquivalent() : 1);
    }

    private void triggerMountLeapEffect(AbstractEntityCitizen citizen, Vec3 startPos, Vec3 targetPos) {
        if (!citizen.level().isClientSide()) {
            DragonColoniesNetwork.CHANNEL.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> citizen),
                    new RiderLeapMessage(citizen.getId(), startPos, targetPos, 7)
            );
        }
    }

    @Override
    protected boolean inventoryNeedsDump() {
        if (this.worker == null || !this.canBeInterrupted()) {
            return false;
        }

        if (this.worker.getCitizenInventoryHandler().isInventoryFull()) {
            return true;
        }

        if (((com.minecolonies.core.colony.jobs.AbstractJob<?, ?>) this.job).getActionsDone() >= this.getActionsDoneUntilDumping()) {
            return hasDumpableItems();
        }

        return false;
    }

    private boolean hasDumpableItems() {
        if (this.worker == null) return false;
        var inv = this.worker.getInventoryCitizen();
        for (int i = 0; i < inv.getSlots(); i++) {
            net.minecraft.world.item.ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty()) {
                if (!(stack.getItem() instanceof net.minecraft.world.item.ArmorItem)
                        && !(stack.getItem() instanceof net.minecraft.world.item.TieredItem)) {
                    return true;
                }
            }
        }
        ((com.minecolonies.core.colony.jobs.AbstractJob<?, ?>) this.job).clearActionsDone();
        return false;
    }

    @Override
    public void tick() {
        if (this.worker != null) {
            RIDERS_BY_CITIZEN.put(this.worker.getUUID(), this);
        }

        super.tick();
        this.radarTickCounter++;

        if (this.worker != null && !this.worker.level().isClientSide()) {
            DragonBase currentDragon = getAssignedDragon();

            if (isMountingDragon) {
                handleMountingPhase(this.worker);
            }

            if (currentDragon != null) {
                boolean isGroundTask = !hasAxe() || isReturningDragon;
                this.flightController.handleFlight(
                        this.worker,
                        currentDragon,
                        this.building,
                        this.radarTickCounter,
                        this.currentPatrolPoint,
                        isGroundTask
                );
            }
        }
    }

    @Override
    protected IAIState decide() {
        if (this.worker == null || !(this.worker.level() instanceof ServerLevel level)) {
            return super.decide();
        }

        if (this.retrievedCooldownTicks > 0) {
            this.retrievedCooldownTicks--;
        }

        long currentTime = level.getGameTime();
        syncAssignedDragon(level);

        boolean workerHasAxe = hasAxe();
        boolean shouldWork = workerHasAxe && this.building != null;

        DragonBase currentDragon = getAssignedDragon();

        if (shouldWork && currentDragon == null && !isMountingDragon && !isReturningDragon) {
            if (this.worker.getVehicle() instanceof DragonBase ridingDragon) {
                this.assignedDragon = ridingDragon;
                setAssignedDragonUUID(ridingDragon.getUUID());
                ridingDragon.getPersistentData().putUUID("DragonColonies_GuardUUID", this.worker.getUUID());
            } else if (currentTime >= this.nextRetrieveAllowedTime) {
                DragonColonies.debug("AI", "[RETRIEVE-TRY] Wache {} versucht Drachen abzurufen (Axt: {}, ZeitOK: true)",
                        this.worker.getName().getString(), workerHasAxe);
                boolean retrieved = tryRetrieveDragonFromRoost(level);
                this.nextRetrieveAllowedTime = currentTime + (retrieved ? 200 : 40);
                DragonColonies.debug("AI", "[RETRIEVE-RESULT] Wache {} Abruf-Ergebnis: {}",
                        this.worker.getName().getString(), retrieved);
            }
        } else if (!shouldWork && currentDragon == null && this.radarTickCounter % 40 == 0) {
            DragonColonies.debug("AI", "[RETRIEVE-BLOCKED] Wache {} kann nicht arbeiten: HasAxe={}, Building={}",
                    this.worker.getName().getString(), workerHasAxe, this.building != null);
        }

        if (shouldWork && currentDragon != null && currentDragon.isAlive() && !isReturningDragon && this.worker.getVehicle() != currentDragon) {
            this.isMountingDragon = true;
        }

        boolean dragonNeedsReturn = currentDragon != null && currentDragon.isAlive() && !DragonStatusHelper.isFitToFly(currentDragon);

        if ((!workerHasAxe || dragonNeedsReturn) && currentDragon != null) {
            if (!isReturningDragon) {
                isReturningDragon = true;
                currentDragon.setTarget(null);
                DragonColonies.debug("NAVIGATION", "[RETURN-TRIGGER] Rueckruf eingeleitet | WacheAxt: {} | FitToFly: {}",
                        workerHasAxe, !dragonNeedsReturn);
            }
            handleReturnDragonToRoost(level);
            return super.decide();
        }

        if (isReturningDragon && currentDragon != null) {
            handleReturnDragonToRoost(level);
            return super.decide();
        }

        return super.decide();
    }

    @Override
    public IAIState patrol() {
        if (this.buildingGuards == null || !(this.worker.level() instanceof ServerLevel level)) {
            return super.patrol();
        }

        if (this.worker.getTarget() != null && this.worker.getTarget().isAlive()) {
            return null;
        }
        if (this.worker instanceof com.minecolonies.api.entity.ai.combat.threat.IThreatTableEntity threatEntity) {
            LivingEntity threatTarget = threatEntity.getThreatTable().getTargetMob();
            if (threatTarget != null && threatTarget.isAlive()) {
                return null;
            }
        }

        DragonBase currentDragon = getAssignedDragon();
        boolean hasDragon = currentDragon != null && currentDragon.isAlive();

        if (!hasDragon) {
            return super.patrol();
        }

        if (this.worker.getVehicle() != currentDragon) {
            this.isMountingDragon = true;
            return null;
        }

        if (this.buildingGuards.requiresManualTarget()) {
            if (this.currentPatrolPoint == null || this.walkToSafePos(this.currentPatrolPoint)) {
                this.currentPatrolPoint = null;
                this.setCurrentDelay(hasDragon ? 20 : 10);

                if (this.worker.getRandom().nextInt(5) <= 1) {
                    BlockPos rawRandom = this.randomPatrolPoint();
                    if (rawRandom != null) {
                        this.currentPatrolPoint = DragonNavigationHandler.getHighAirPos(level, rawRandom);
                        this.walkToSafePos(this.currentPatrolPoint);
                    }
                }
            }
        } else {
            BlockPos rawTarget = this.buildingGuards.getNextPatrolTarget(false);

            if (rawTarget != null) {
                if (rawTarget.equals(this.lastReachedPatrolPoint)) {
                    if (this.currentPatrolPoint == null || this.walkToSafePos(this.currentPatrolPoint)) {
                        this.currentPatrolPoint = calculateKinematicOrbitPoint(currentDragon, rawTarget, level);
                        this.walkToSafePos(this.currentPatrolPoint);
                        this.setCurrentDelay(20);
                    }
                } else {
                    this.currentPatrolPoint = DragonNavigationHandler.getHighAirPos(level, rawTarget);

                    if (this.walkToSafePos(this.currentPatrolPoint)) {
                        this.setCurrentDelay(hasDragon ? 20 : 0);
                        this.lastReachedPatrolPoint = rawTarget;
                        this.buildingGuards.arrivedAtPatrolPoint(this.worker);

                        int maxAltitude = this.currentPatrolPoint.getY();
                        int radius = 35;
                        for (int i = 0; i < 8; i++) {
                            double angle = i * (Math.PI / 4);
                            int scanX = rawTarget.getX() + (int)(Math.cos(angle) * radius);
                            int scanZ = rawTarget.getZ() + (int)(Math.sin(angle) * radius);
                            int surfaceY = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(scanX, 0, scanZ)).getY();
                            maxAltitude = Math.max(maxAltitude, surfaceY + 12);
                        }
                        this.cachedLoiterAltitude = maxAltitude;
                    }
                }
            }
        }

        return null;
    }

    private BlockPos calculateKinematicOrbitPoint(DragonBase dragon, BlockPos center, ServerLevel level) {
        Vec3 dragonPos = dragon.position();
        double dx = dragonPos.x - (center.getX() + 0.5D);
        double dz = dragonPos.z - (center.getZ() + 0.5D);
        double distFromCenter = Math.sqrt(dx * dx + dz * dz);

        float yaw = dragon.getYRot();

        if (distFromCenter > 45.0D) {
            float toCenterYaw = (float) (Mth.atan2(-dz, -dx) * (180.0D / Math.PI)) - 90.0F;
            yaw = Mth.rotLerp(0.35F, yaw, toCenterYaw);
        } else {
            yaw += 25.0F;
        }

        double rad = Math.toRadians(yaw + 90.0F);
        double forwardDist = 18.0D;
        double targetX = dragonPos.x + Math.cos(rad) * forwardDist;
        double targetZ = dragonPos.z + Math.sin(rad) * forwardDist;
        int safeY = this.cachedLoiterAltitude != -1 ? this.cachedLoiterAltitude : DragonNavigationHandler.getHighAirPos(level, center).getY();

        return new BlockPos(Mth.floor(targetX), safeY, Mth.floor(targetZ));
    }

    public void handleMountingPhase(AbstractEntityCitizen citizen) {
        DragonBase currentDragon = getAssignedDragon();
        if (currentDragon == null || !currentDragon.isAlive()) {
            isMountingDragon = false;
            mountingTicks = 0;
            return;
        }

        boolean alreadyRiding = citizen.isPassenger() && (citizen.getVehicle() == currentDragon || currentDragon.getPassengers().contains(citizen));
        if (alreadyRiding) {
            isMountingDragon = false;
            mountingTicks = 0;
            return;
        }

        if (!citizen.isPassenger()) {
            this.mountingTicks++;
            double distSqr = citizen.distanceToSqr(currentDragon);
            boolean navInProgress = currentDragon.getNavigation() != null && currentDragon.getNavigation().isInProgress();

            if (this.mountingTicks % 10 == 0 || this.mountingTicks == 1) {
                DragonColonies.debug("NAVIGATION", "[MOUNT-PHASE] Tick: {} | DistSqr: {} | DragonNavInProgress: {} | CitizenPos: {} | DragonPos: {}",
                        this.mountingTicks, String.format("%.2f", distSqr), navInProgress, citizen.blockPosition().toShortString(), currentDragon.blockPosition().toShortString());
            }

            if (currentDragon.getNavigation() != null && !navInProgress) {
                boolean navMoved = currentDragon.getNavigation().moveTo(citizen, 1.35D);
                DragonColonies.debug("NAVIGATION", "[MOUNT-NAV] Drache moveTo(citizen) aufgerufen -> Ergebnis: {}", navMoved);
            }

            if (distSqr <= 16.0D) {
                Vec3 startPos = citizen.position();
                Vec3 targetPos = new Vec3(currentDragon.getX(), currentDragon.getY() + 0.5D, currentDragon.getZ());

                triggerMountLeapEffect(citizen, startPos, targetPos);

                boolean mounted = citizen.startRiding(currentDragon, true);
                DragonColonies.debug("NAVIGATION", "[MOUNT-TRY] startRiding(standard) ausgefuehrt -> Erfolg: {}", mounted);
                if (mounted) {
                    this.isMountingDragon = false;
                    this.mountingTicks = 0;
                    currentDragon.setCommand(0);
                    currentDragon.setGroundStance(GroundStance.IDLE);
                    currentDragon.getPersistentData().putUUID("DragonColonies_GuardUUID", citizen.getUUID());
                    return;
                }
            }

            if (this.mountingTicks >= MAX_MOUNT_TICKS || citizen.getNavigation().isStuck()) {
                Vec3 startPos = citizen.position();
                Vec3 targetPos = new Vec3(currentDragon.getX(), currentDragon.getY() + 0.5D, currentDragon.getZ());

                DragonColonies.debug("NAVIGATION", "[MOUNT-FORCE] Timeout/Stuck erreicht (Ticks: {}) -> Teleportiere Citizen zu Drache", this.mountingTicks);

                triggerMountLeapEffect(citizen, startPos, targetPos);

                citizen.moveTo(targetPos.x, targetPos.y, targetPos.z);
                boolean mounted = citizen.startRiding(currentDragon, true);
                DragonColonies.debug("NAVIGATION", "[MOUNT-FORCE] startRiding(force) ausgefuehrt -> Erfolg: {}", mounted);
                if (mounted) {
                    this.isMountingDragon = false;
                    this.mountingTicks = 0;
                    currentDragon.getNavigation().stop();
                    currentDragon.setCommand(0);
                    currentDragon.setGroundStance(GroundStance.IDLE);
                    currentDragon.getPersistentData().putUUID("DragonColonies_GuardUUID", citizen.getUUID());
                }
            }
        }
    }

    public IAIState handleDragonControl() {
        if (this.worker != null && getAssignedDragon() != null) {
            RIDERS_BY_CITIZEN.put(this.worker.getUUID(), this);
        }
        return null;
    }

    private void syncAssignedDragon(ServerLevel level) {
        if (this.worker != null && this.worker.getVehicle() instanceof DragonBase ridingDragon) {
            this.assignedDragon = ridingDragon;
            setAssignedDragonUUID(ridingDragon.getUUID());
            ridingDragon.getPersistentData().putUUID("DragonColonies_GuardUUID", this.worker.getUUID());
            return;
        }

        UUID currentUUID = getAssignedDragonUUID();
        if (currentUUID != null && this.assignedDragon == null) {
            Entity existingEntity = level.getEntity(currentUUID);
            if (existingEntity instanceof DragonBase dragon && dragon.isAlive()) {
                this.assignedDragon = dragon;
                dragon.getPersistentData().putUUID("DragonColonies_GuardUUID", this.worker.getUUID());
            }
        }

        if (this.assignedDragon != null && (this.assignedDragon.isRemoved() || !this.assignedDragon.isAlive())) {
            this.assignedDragon = null;
            setAssignedDragonUUID(null);
        }

        if (this.assignedDragon == null) {
            this.isReturningDragon = false;
            this.isMountingDragon = false;
        }
    }

    public boolean hasDragonOrCanGetOne() {
        DragonBase current = getAssignedDragon();
        if (current != null && current.isAlive()) {
            return true;
        }
        return isDragonAvailableInRoost();
    }

    public boolean isDragonAvailableInRoost() {
        if (!(this.building instanceof BuildingDragonRoost roost)) return false;
        DragonStorageModule storage = roost.getStorageModule();
        if (storage == null) return false;

        List<CompoundTag> stored = storage.getStoredDragons();
        if (stored == null || stored.isEmpty()) return false;

        for (CompoundTag tag : stored) {
            if (DragonStatusHelper.isFitToFly(tag)) {
                return true;
            }
        }
        return false;
    }

    private boolean tryRetrieveDragonFromRoost(ServerLevel level) {
        if (!(this.building instanceof BuildingDragonRoost roost)) {
            DragonColonies.debug("AI", "[RETRIEVE-FAIL] Gebaeude ist kein BuildingDragonRoost");
            return false;
        }

        DragonStorageModule storage = roost.getStorageModule();
        if (storage == null) {
            DragonColonies.debug("AI", "[RETRIEVE-FAIL] StorageModule ist null");
            return false;
        }

        List<CompoundTag> stored = storage.getStoredDragons();
        if (stored == null || stored.isEmpty()) {
            DragonColonies.debug("AI", "[RETRIEVE-FAIL] Keine Drachen im Hort gespeichert");
            return false;
        }

        int workerCitizenId = this.worker.getCitizenData() != null ? this.worker.getCitizenData().getId() : -1;
        CompoundTag targetDragonNbt = null;

        for (int i = 0; i < stored.size(); i++) {
            CompoundTag tag = stored.get(i);
            boolean fit = DragonStatusHelper.isFitToFly(tag);
            String mode = tag.getString(DragonStorageModule.TAG_ASSIGNMENT_MODE);
            int assignedId = tag.getInt(DragonStorageModule.TAG_ASSIGNED_CITIZEN_ID);

            DragonColonies.debug("AI", "[RETRIEVE-SCAN] Drache #{}: Fit={}, Mode='{}', AssignedId={}, WorkerId={}",
                    i, fit, mode, assignedId, workerCitizenId);

            if (fit) {
                if (DragonStorageModule.MODE_ASSIGNED.equals(mode) && assignedId == workerCitizenId) {
                    targetDragonNbt = tag;
                    DragonColonies.debug("AI", "[RETRIEVE-MATCH] Passender zugewiesener Drache gefunden (Index {})", i);
                    break;
                }
            }
        }

        if (targetDragonNbt == null) {
            for (int i = 0; i < stored.size(); i++) {
                CompoundTag tag = stored.get(i);
                if (DragonStatusHelper.isFitToFly(tag)) {
                    String mode = tag.getString(DragonStorageModule.TAG_ASSIGNMENT_MODE);
                    boolean isAuto = mode.isEmpty() || DragonStorageModule.MODE_AUTO.equals(mode);
                    if (isAuto) {
                        targetDragonNbt = tag;
                        DragonColonies.debug("AI", "[RETRIEVE-MATCH] Passender AUTO-Drache gefunden (Index {})", i);
                        break;
                    }
                }
            }
        }

        if (targetDragonNbt == null) {
            DragonColonies.debug("AI", "[RETRIEVE-FAIL] Kein einsatzbereiter Drache fuer Wache {} verfuegbar",
                    this.worker.getName().getString());
            return false;
        }

        UUID roostDragonId = targetDragonNbt.hasUUID(DragonStorageModule.TAG_ROOST_DRAGON_ID)
                ? targetDragonNbt.getUUID(DragonStorageModule.TAG_ROOST_DRAGON_ID)
                : UUID.randomUUID();

        DragonBase dragon = storage.deployDragon(roostDragonId, level, roost.getPosition(), this.worker.getUUID());
        if (dragon != null) {
            this.assignedDragon = dragon;
            setAssignedDragonUUID(dragon.getUUID());
            this.isReturningDragon = false;
            this.isMountingDragon = true;
            DragonColonies.debug("NAVIGATION", "[DEPLOY] Drache {} aus Hort geholt fuer Wache {}",
                    dragon.getName().getString(), this.worker.getName().getString());
            return true;
        }

        DragonColonies.debug("AI", "[RETRIEVE-FAIL] deployDragon schlug fehl fuer RoostID {}", roostDragonId);
        return false;
    }

    private void handleReturnDragonToRoost(ServerLevel level) {
        DragonBase currentDragon = getAssignedDragon();
        if (currentDragon == null || !isReturningDragon) {
            return;
        }

        if (this.building instanceof BuildingDragonRoost roost) {
            BlockPos roostPos = roost.getPosition();

            this.walkToSafePos(roostPos);

            double trueDistance = currentDragon.distanceToSqr(
                    roostPos.getX() + 0.5,
                    roostPos.getY() + 1.0,
                    roostPos.getZ() + 0.5
            );

            if (trueDistance < 16.0D) {
                if (this.worker.isPassenger() || currentDragon.getPassengers().contains(this.worker)) {
                    this.worker.stopRiding();
                }

                DragonStorageModule storage = roost.getStorageModule();
                if (storage != null && storage.storeDragon(currentDragon)) {
                    DragonColonies.debug("NAVIGATION", "[RETURN-STORE] Drache {} im Hort eingelagert (Dist: {}m)",
                            currentDragon.getName().getString(), String.format("%.1f", Math.sqrt(trueDistance)));
                    this.assignedDragon = null;
                    setAssignedDragonUUID(null);
                    this.isReturningDragon = false;
                    this.isMountingDragon = false;
                    this.currentReturnTarget = null;
                }
            }
        }
    }

    public void triggerDragonReturn() {
        DragonBase currentDragon = getAssignedDragon();
        if (!this.isReturningDragon && currentDragon != null) {
            this.isReturningDragon = true;
            currentDragon.setTarget(null);
            OmniAttackHandler h = (OmniAttackHandler) currentDragon.componentRegistry.get(OmniAttackHandler.class);
            if (h != null && h.isAttacking()) {
                h.handlePlayerFiring(false);
            }
        }
    }

    public AbstractEntityCitizen getCitizen() {
        return this.worker;
    }

    private int decrementAndGetSleepTimer() {
        if (SLEEP_TIMER_FIELD != null) {
            try {
                int current = SLEEP_TIMER_FIELD.getInt(this) - this.getTickRate();
                SLEEP_TIMER_FIELD.setInt(this, current);
                return current;
            } catch (Exception ignored) {}
        }
        return 0;
    }

    @Override
    protected IAIState sleep() {
        DragonBase currentDragon = getAssignedDragon();
        if (currentDragon != null && this.isReturningDragon && this.worker != null) {
            int remainingTime = decrementAndGetSleepTimer();

            if (this.worker.level() instanceof ServerLevel level) {
                handleReturnDragonToRoost(level);
            }

            if (this.assignedDragon == null) {
                this.worker.getNavigation().stop();
                com.minecolonies.core.entity.other.SittingEntity.sitDown(
                        this.worker.blockPosition(),
                        this.worker,
                        remainingTime
                );
            }

            if (remainingTime >= 0) {
                return null;
            }
        }

        return super.sleep();
    }

    public void setPatrolPoint(BlockPos pos) {
        this.currentPatrolPoint = pos;
    }
}