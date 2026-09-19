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
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.bookofdragons.entity.component.ranged.OmniAttackHandler;
import net.magister.bookofdragons.entity.state.GroundStance;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.UUID;

public abstract class AbstractEntityAIDragonRider<J extends AbstractJobGuard<J>, B extends AbstractBuildingGuards> extends AbstractEntityAIGuard<J, B> {

    protected DragonBase assignedDragon = null;
    protected boolean isMountingDragon = false;
    protected boolean isReturningDragon = false;
    protected int retrievedCooldownTicks = 0;
    private Vec3 currentReturnTarget = null;
    private long nextRetrieveAllowedTime = 0;
    private final MountedDragonController flightController = new MountedDragonController();
    private static final java.util.Map<java.util.UUID, AbstractEntityAIDragonRider<?, ?>> RIDERS_BY_CITIZEN = new java.util.concurrent.ConcurrentHashMap<>();
    private static java.lang.reflect.Field SLEEP_TIMER_FIELD;

    private BlockPos lastReachedPatrolPoint = null;
    private float loiterAngle = 0.0F;
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
    public void tick() {
        if (this.worker != null) {
            RIDERS_BY_CITIZEN.put(this.worker.getUUID(), this);
        }

        super.tick();
        this.radarTickCounter++;

        if (this.worker != null && !this.worker.level().isClientSide()) {
            DragonBase currentDragon = getAssignedDragon();

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
                boolean retrieved = tryRetrieveDragonFromRoost(level);
                this.nextRetrieveAllowedTime = currentTime + (retrieved ? 200 : 40);
            }
        }

        if (!workerHasAxe && currentDragon != null) {
            if (!isReturningDragon) {
                isReturningDragon = true;
                currentDragon.setTarget(null);
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

        DragonBase currentDragon = getAssignedDragon();
        boolean hasDragon = currentDragon != null && currentDragon.isAlive();

        if (this.buildingGuards.requiresManualTarget()) {
            if (this.currentPatrolPoint == null || this.walkToSafePos(this.currentPatrolPoint)) {
                this.currentPatrolPoint = null;
                this.setCurrentDelay(hasDragon ? 0 : 10);

                if (this.worker.getRandom().nextInt(5) <= 1) {
                    BlockPos rawRandom = this.randomPatrolPoint();
                    if (rawRandom != null) {
                        this.currentPatrolPoint = hasDragon ? DragonNavigationHandler.getHighAirPos(level, rawRandom) : rawRandom;
                        this.walkToSafePos(this.currentPatrolPoint);
                    }
                }
            }
        } else {
            BlockPos rawTarget = this.buildingGuards.getNextPatrolTarget(false);

            if (rawTarget != null) {
                if (hasDragon && rawTarget.equals(this.lastReachedPatrolPoint)) {
                    this.loiterAngle += 0.05F;
                    if (this.loiterAngle > (float) (Math.PI * 2)) this.loiterAngle -= (float) (Math.PI * 2);

                    int radius = 35;
                    double offsetX = Math.cos(this.loiterAngle) * radius;
                    double offsetZ = Math.sin(this.loiterAngle) * radius;

                    int safeY = this.cachedLoiterAltitude != -1 ? this.cachedLoiterAltitude : DragonNavigationHandler.getHighAirPos(level, rawTarget).getY();

                    this.currentPatrolPoint = BlockPos.containing(
                            rawTarget.getX() + offsetX,
                            safeY,
                            rawTarget.getZ() + offsetZ
                    );

                    this.walkToSafePos(this.currentPatrolPoint);
                    this.setCurrentDelay(0);

                } else {
                    this.currentPatrolPoint = hasDragon ? DragonNavigationHandler.getHighAirPos(level, rawTarget) : rawTarget;

                    if (this.walkToSafePos(this.currentPatrolPoint)) {
                        this.setCurrentDelay(hasDragon ? 0 : 10);
                        this.lastReachedPatrolPoint = rawTarget;
                        this.buildingGuards.arrivedAtPatrolPoint(this.worker);

                        if (hasDragon) {
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
                        } else {
                            this.cachedLoiterAltitude = -1;
                        }
                    }
                }
            }
        }

        return null;
    }

    public void handleMountingPhase(AbstractEntityCitizen citizen) {
        DragonBase currentDragon = getAssignedDragon();
        if (currentDragon == null || !currentDragon.isAlive()) {
            isMountingDragon = false;
            return;
        }

        boolean alreadyRiding = citizen.isPassenger() && (citizen.getVehicle() == currentDragon || currentDragon.getPassengers().contains(citizen));

        if (!alreadyRiding && !citizen.isPassenger()) {
            citizen.moveTo(currentDragon.getX(), currentDragon.getY() + 0.5, currentDragon.getZ());
            boolean mounted = citizen.startRiding(currentDragon, true);
            if (mounted) {
                isMountingDragon = false;
                currentDragon.setCommand(0);
                currentDragon.setGroundStance(GroundStance.IDLE);
                currentDragon.getPersistentData().putUUID("DragonColonies_GuardUUID", citizen.getUUID());
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

    private boolean tryRetrieveDragonFromRoost(ServerLevel level) {
        if (!(this.building instanceof BuildingDragonRoost roost)) return false;

        DragonStorageModule storage = roost.getStorageModule();
        if (storage == null) return false;

        List<CompoundTag> stored = storage.getStoredDragons();
        if (stored == null || stored.isEmpty()) return false;

        CompoundTag targetDragonNbt = null;
        UUID guardUuid = this.worker.getUUID();

        for (CompoundTag tag : stored) {
            if (tag.hasUUID(DragonStorageModule.TAG_GUARD_UUID) && tag.getUUID(DragonStorageModule.TAG_GUARD_UUID).equals(guardUuid)) {
                if (!tag.getBoolean(DragonStorageModule.TAG_IS_DEAD)) {
                    targetDragonNbt = tag;
                    break;
                }
            }
        }

        if (targetDragonNbt == null) {
            for (CompoundTag tag : stored) {
                if (!tag.getBoolean(DragonStorageModule.TAG_DEPLOYED)
                        && !tag.getBoolean(DragonStorageModule.TAG_IS_DEAD)
                        && !tag.hasUUID(DragonStorageModule.TAG_GUARD_UUID)
                        && isDragonFit(tag)) {
                    targetDragonNbt = tag;
                    targetDragonNbt.putUUID(DragonStorageModule.TAG_GUARD_UUID, guardUuid);
                    break;
                }
            }
        }

        if (targetDragonNbt == null) return false;

        UUID roostDragonId = targetDragonNbt.hasUUID(DragonStorageModule.TAG_ROOST_DRAGON_ID)
                ? targetDragonNbt.getUUID(DragonStorageModule.TAG_ROOST_DRAGON_ID)
                : UUID.randomUUID();
        targetDragonNbt.putUUID(DragonStorageModule.TAG_ROOST_DRAGON_ID, roostDragonId);

        UUID oldActiveUuid = targetDragonNbt.hasUUID(DragonStorageModule.TAG_ACTIVE_ENTITY_UUID)
                ? targetDragonNbt.getUUID(DragonStorageModule.TAG_ACTIVE_ENTITY_UUID)
                : null;

        if (oldActiveUuid != null && level.getEntity(oldActiveUuid) instanceof DragonBase existingDragon && existingDragon.isAlive()) {
            storage.setDeployedStatus(roostDragonId, true, oldActiveUuid);
            this.assignedDragon = existingDragon;
            existingDragon.getPersistentData().putUUID("DragonColonies_GuardUUID", this.worker.getUUID());
            existingDragon.getPersistentData().putUUID("DragonColonies_RoostDragonID", roostDragonId);
            return true;
        }

        if (!targetDragonNbt.contains("id") && targetDragonNbt.contains("DragonType")) {
            targetDragonNbt.putString("id", "bookofdragons:" + targetDragonNbt.getString("DragonType").toLowerCase());
        }

        if (targetDragonNbt.contains("CustomName") && !targetDragonNbt.getString("CustomName").startsWith("{")) {
            targetDragonNbt.remove("CustomName");
        }

        UUID newUuid = UUID.randomUUID();
        targetDragonNbt.putUUID("UUID", newUuid);
        storage.setDeployedStatus(roostDragonId, true, newUuid);
        storage.markDirty();

        try {
            Entity entity = EntityType.loadEntityRecursive(targetDragonNbt, level, (e) -> {
                BlockPos spawnPos = roost.getPosition();
                e.moveTo(spawnPos.getX() + 0.5, spawnPos.getY() + 1.0, spawnPos.getZ() + 0.5, 0, 0);

                e.getPersistentData().putLong("DragonColonies_RoostPos", roost.getPosition().asLong());
                e.getPersistentData().putUUID("DragonColonies_RoostDragonID", roostDragonId);
                e.getPersistentData().putBoolean("DragonColonies_GuardDeployed", true);
                e.getPersistentData().putUUID("DragonColonies_GuardUUID", this.worker.getUUID());
                return e;
            });

            if (entity instanceof DragonBase dragon) {
                dragon.setSaddled(true);
                level.addFreshEntity(dragon);

                this.assignedDragon = dragon;
                this.isReturningDragon = false;
                this.isMountingDragon = false;

                this.worker.moveTo(dragon.getX(), dragon.getY() + 0.5, dragon.getZ());
                this.worker.startRiding(dragon, true);

                dragon.setCommand(0);
                dragon.setGroundStance(GroundStance.IDLE);

                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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
                if (storage != null) {
                    currentDragon.ejectPassengers();

                    CompoundTag dragonNbt = new CompoundTag();
                    currentDragon.saveWithoutId(dragonNbt);

                    ResourceLocation entityKey = ForgeRegistries.ENTITY_TYPES.getKey(currentDragon.getType());
                    if (entityKey != null) {
                        dragonNbt.putString("id", entityKey.toString());
                    }
                    dragonNbt.remove("Passengers");

                    String dragonDisplayName = currentDragon.hasCustomName() ? currentDragon.getCustomName().getString() : currentDragon.getName().getString();
                    dragonNbt.putString("CustomName", Component.Serializer.toJson(Component.literal(dragonDisplayName)));

                    UUID roostId = currentDragon.getPersistentData().hasUUID("DragonColonies_RoostDragonID")
                            ? currentDragon.getPersistentData().getUUID("DragonColonies_RoostDragonID")
                            : currentDragon.getUUID();

                    storage.updateDragonData(roostId, dragonNbt);

                    currentDragon.getPersistentData().remove("DragonColonies_RoostPos");
                    currentDragon.getPersistentData().remove("DragonColonies_RoostDragonID");
                    currentDragon.getPersistentData().remove("DragonColonies_GuardDeployed");
                    currentDragon.getPersistentData().remove("DragonColonies_GuardUUID");

                    currentDragon.discard();
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

    public boolean isDragonAvailableInRoost() {
        if (!(this.building instanceof BuildingDragonRoost roost)) return false;
        DragonStorageModule storage = roost.getStorageModule();
        if (storage == null) return false;

        List<CompoundTag> stored = storage.getStoredDragons();
        if (stored == null || stored.isEmpty()) return false;

        for (CompoundTag tag : stored) {
            if (!tag.getBoolean(DragonStorageModule.TAG_DEPLOYED) && !tag.getBoolean(DragonStorageModule.TAG_IS_DEAD) && isDragonFit(tag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Prüft autark, ob der Drache fit für den Wachtdienst ist.
     * Zucht-Drachen, Eier und Babys werden konsequent abgelehnt!
     */
    private boolean isDragonFit(CompoundTag dragonTag) {
        if (dragonTag == null) return false;
        
        // 1. Zucht / Ei Sicherheits-Filter
        if (dragonTag.getBoolean("DragonColonies_IsEgg")) return false;
        if (dragonTag.getBoolean("DragonColonies_AllowBreeding")) return false;
        
        // 2. Muss mindestens Broad Wing (Stage 2) sein, um fliegen/kämpfen zu können
        if (dragonTag.contains("GrowthStage") && dragonTag.getInt("GrowthStage") < 2) return false;
        
        // 3. Sättigung prüfen
        if (dragonTag.contains("dragonNeeds")) {
            CompoundTag needs = dragonTag.getCompound("dragonNeeds");
            if (needs.contains("foodLevel")) {
                return needs.getInt("foodLevel") >= 60;
            }
        }
        
        return true;
    }
}