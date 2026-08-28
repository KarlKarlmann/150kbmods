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

import java.util.List;
import java.util.UUID;

public abstract class AbstractEntityAIDragonRider<J extends AbstractJobGuard<J>, B extends AbstractBuildingGuards> extends AbstractEntityAIGuard<J, B> {

    protected DragonBase assignedDragon = null;
    protected UUID assignedDragonUUID = null;
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
        return citizen != null ? RIDERS_BY_CITIZEN.get(citizen.getUUID()) : null;
    }

    public DragonBase getAssignedDragon() {
        return this.assignedDragon;
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
            // 1. Phasen & Flugsteuerung (inkl. Feindradar) an den Controller übergeben
            if (this.assignedDragon != null) {
                boolean isGroundTask = !hasAxe() || isReturningDragon;
                this.flightController.handleFlight(
                    this.worker, 
                    this.assignedDragon, 
                    this.building, 
                    this.radarTickCounter, 
                    this.currentPatrolPoint, 
                    isGroundTask
                );
            }

            // 2. Debug Logging
            if (this.radarTickCounter % 40 == 0) {
                String name = this.worker.getName().getString();
                BlockPos pos = this.worker.blockPosition();
                String vehicle = (this.worker.getVehicle() != null) ? this.worker.getVehicle().getType().getDescriptionId() : "BODEN";
                String mcState = (this.getState() != null) ? this.getState().toString() : "KEIN_STATE";
                String task = (this.building != null) ? this.building.getTask() : "KEIN_GEBÄUDE";
                LivingEntity target = this.worker.getTarget();
                String targetName = (target != null) ? target.getName().getString() : "KEINS";
                BlockPos patrolPos = this.currentPatrolPoint;

                DragonColonies.LOGGER.info(
                    "[GUARD-RADAR] {} | Pos: {} | Stand: {} | MC-State: {} | Task: {} | Target: {} | PatrouilleZiel: {} | Returning: {} | HasDragon: {}",
                    name,
                    pos.toShortString(),
                    vehicle,
                    mcState,
                    task,
                    targetName,
                    (patrolPos != null ? patrolPos.toShortString() : "KEINS"),
                    this.isReturningDragon,
                    (this.assignedDragon != null)
                );
            }

            // 3. Navigation anhalten, wenn die Wache im Flug ist
            if (this.assignedDragon != null && this.worker.isPassenger() && this.worker.getVehicle() == this.assignedDragon) {
                if (this.assignedDragon.getTransportMode() == net.magister.bookofdragons.entity.state.TransportMode.AIRBORNE) {
                    this.assignedDragon.getNavigation().stop();
                }
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

        // =========================================================================
        // 1. LIFECYCLE: Drachen aus dem Hort holen (wenn keiner da ist)
        // =========================================================================
        if (shouldWork && this.assignedDragon == null && !isMountingDragon && !isReturningDragon) {
            if (this.worker.getVehicle() instanceof DragonBase ridingDragon) {
                this.assignedDragon = ridingDragon;
                this.assignedDragonUUID = ridingDragon.getUUID();
                ridingDragon.getPersistentData().putUUID("DragonColonies_GuardUUID", this.worker.getUUID());
            } else if (currentTime >= this.nextRetrieveAllowedTime) {
                boolean retrieved = tryRetrieveDragonFromRoost(level);
                this.nextRetrieveAllowedTime = currentTime + (retrieved ? 200 : 40);
            }
        }

        // =========================================================================
        // 2. LIFECYCLE: Drachen zurückbringen (Feierabend / Waffe verloren)
        // =========================================================================
        if (!workerHasAxe && this.assignedDragon != null) {
            if (!isReturningDragon) {
                isReturningDragon = true;
                this.assignedDragon.setTarget(null);
            }
            handleReturnDragonToRoost(level);
            return super.decide();
        }

        if (isReturningDragon && this.assignedDragon != null) {
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

        boolean hasDragon = this.assignedDragon != null && this.assignedDragon.isAlive();

        // =========================================================================
        // A) MANUELLE PATROUILLE
        // =========================================================================
        if (this.buildingGuards.requiresManualTarget()) {
            if (this.currentPatrolPoint == null || this.walkToSafePos(this.currentPatrolPoint)) {
                this.currentPatrolPoint = null;
                this.setCurrentDelay(hasDragon ? 0 : 10);

                if (this.worker.getRandom().nextInt(5) <= 1) {
                    BlockPos rawRandom = this.randomPatrolPoint();
                    if (rawRandom != null) {
                        this.currentPatrolPoint = hasDragon ? DragonTargetResolver.getHighAirPos(level, rawRandom) : rawRandom;
                        this.walkToSafePos(this.currentPatrolPoint); 
                    }
                }
            }
        } 
        // =========================================================================
        // B) AUTOMATISCHE PATROUILLE (mit Loitering)
        // =========================================================================
        else {
            BlockPos rawTarget = this.buildingGuards.getNextPatrolTarget(false);
            
            if (rawTarget != null) {
                
                // 1. WARTESCHLEIFE (Loitering): Ziel erreicht, Warten auf Turm-Freigabe
                if (hasDragon && rawTarget.equals(this.lastReachedPatrolPoint)) {
                    
                    this.loiterAngle += 0.05F; 
                    if (this.loiterAngle > (float) (Math.PI * 2)) this.loiterAngle -= (float) (Math.PI * 2);
                    
                    BlockPos highAirCenter = DragonTargetResolver.getHighAirPos(level, rawTarget);
                    
                    int radius = 35;
                    double offsetX = Math.cos(this.loiterAngle) * radius;
                    double offsetZ = Math.sin(this.loiterAngle) * radius;
                    
                    this.currentPatrolPoint = BlockPos.containing(
                        highAirCenter.getX() + offsetX, 
                        highAirCenter.getY(), 
                        highAirCenter.getZ() + offsetZ
                    );
                    
                    this.walkToSafePos(this.currentPatrolPoint);
                    this.setCurrentDelay(0);
                    
                } 
                // 2. NEUES ZIEL VOM TURM (Oder Wache geht zu Fuß)
                else {
                    this.currentPatrolPoint = hasDragon ? DragonTargetResolver.getHighAirPos(level, rawTarget) : rawTarget;

                    if (this.walkToSafePos(this.currentPatrolPoint)) {
                        this.setCurrentDelay(hasDragon ? 0 : 10);
                        this.lastReachedPatrolPoint = rawTarget;
                        this.buildingGuards.arrivedAtPatrolPoint(this.worker);
                    }
                }
            }
        }

        return null;
    }

    public void handleMountingPhase(AbstractEntityCitizen citizen) {
        if (assignedDragon == null || !assignedDragon.isAlive()) {
            isMountingDragon = false;
            return;
        }

        boolean alreadyRiding = citizen.isPassenger() && (citizen.getVehicle() == assignedDragon || assignedDragon.getPassengers().contains(citizen));

        if (!alreadyRiding && !citizen.isPassenger()) {
            citizen.moveTo(assignedDragon.getX(), assignedDragon.getY() + 0.5, assignedDragon.getZ());
            boolean mounted = citizen.startRiding(assignedDragon, true);
            if (mounted) {
                isMountingDragon = false;
                assignedDragon.setCommand(0); 
                assignedDragon.setGroundStance(GroundStance.IDLE);
                // NBT-Stempel sichern
                assignedDragon.getPersistentData().putUUID("DragonColonies_GuardUUID", citizen.getUUID());
            }
        }
    }

    public IAIState handleDragonControl() {
        if (this.worker != null && this.assignedDragon != null) {
            RIDERS_BY_CITIZEN.put(this.worker.getUUID(), this);
        }
        return null;
    }

    private void syncAssignedDragon(ServerLevel level) {
        if (this.worker != null && this.worker.getVehicle() instanceof DragonBase ridingDragon) {
            this.assignedDragon = ridingDragon;
            this.assignedDragonUUID = ridingDragon.getUUID();
            ridingDragon.getPersistentData().putUUID("DragonColonies_GuardUUID", this.worker.getUUID());
            return;
        }

        if (this.assignedDragonUUID != null && this.assignedDragon == null) {
            Entity existingEntity = level.getEntity(this.assignedDragonUUID);
            if (existingEntity instanceof DragonBase dragon && dragon.isAlive()) {
                this.assignedDragon = dragon;
                dragon.getPersistentData().putUUID("DragonColonies_GuardUUID", this.worker.getUUID());
            }
        }

        if (this.assignedDragon != null && (this.assignedDragon.isRemoved() || !this.assignedDragon.isAlive())) {
            this.assignedDragon = null;
            this.assignedDragonUUID = null;
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
        for (CompoundTag tag : stored) {
            if (!tag.getBoolean("Deployed") && !tag.getBoolean("IsDead")) {
                targetDragonNbt = tag;
                break;
            }
        }

        if (targetDragonNbt == null) return false;

        UUID oldDragonId = targetDragonNbt.hasUUID("UUID") ? targetDragonNbt.getUUID("UUID") : null;

        if (oldDragonId != null && level.getEntity(oldDragonId) != null) {
             storage.setDeployedStatus(oldDragonId, true);
             this.assignedDragon = (DragonBase) level.getEntity(oldDragonId);
             this.assignedDragonUUID = oldDragonId;
             this.assignedDragon.getPersistentData().putUUID("DragonColonies_GuardUUID", this.worker.getUUID());
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
        storage.markDirty();

        try {
            Entity entity = EntityType.loadEntityRecursive(targetDragonNbt, level, (e) -> {
                BlockPos spawnPos = roost.getPosition();
                e.moveTo(spawnPos.getX() + 0.5, spawnPos.getY() + 1.0, spawnPos.getZ() + 0.5, 0, 0);
                return e;
            });

            if (entity instanceof DragonBase dragon) {
                dragon.setSaddled(true);

                dragon.getPersistentData().putLong("DragonColonies_RoostPos", roost.getPosition().asLong());
                dragon.getPersistentData().putBoolean("DragonColonies_GuardDeployed", true);
                dragon.getPersistentData().putUUID("DragonColonies_GuardUUID", this.worker.getUUID());

                level.addFreshEntity(dragon);

                storage.setDeployedStatus(newUuid, true);

                this.assignedDragon = dragon;
                this.assignedDragonUUID = dragon.getUUID();
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
        if (this.assignedDragon == null || !isReturningDragon) {
            return;
        }

        if (this.building instanceof BuildingDragonRoost roost) {
            BlockPos roostPos = roost.getPosition();

            this.walkToSafePos(roostPos);

            double trueDistance = this.assignedDragon.distanceToSqr(
                    roostPos.getX() + 0.5,
                    roostPos.getY() + 1.0,
                    roostPos.getZ() + 0.5
            );

            if (trueDistance < 16.0D) {
                if (this.worker.isPassenger() || this.assignedDragon.getPassengers().contains(this.worker)) {
                    this.worker.stopRiding();
                }

                DragonStorageModule storage = roost.getStorageModule();
                if (storage != null) {
                    this.assignedDragon.ejectPassengers();

                    CompoundTag dragonNbt = new CompoundTag();
                    this.assignedDragon.saveWithoutId(dragonNbt);

                    ResourceLocation entityKey = ForgeRegistries.ENTITY_TYPES.getKey(this.assignedDragon.getType());
                    if (entityKey != null) {
                        dragonNbt.putString("id", entityKey.toString());
                    }
                    dragonNbt.remove("Passengers");

                    String dragonDisplayName = this.assignedDragon.hasCustomName() ? this.assignedDragon.getCustomName().getString() : this.assignedDragon.getName().getString();
                    dragonNbt.putString("CustomName", dragonDisplayName);

                    storage.updateDragonData(this.assignedDragon.getUUID(), dragonNbt);

                    this.assignedDragon.getPersistentData().remove("DragonColonies_RoostPos");
                    this.assignedDragon.getPersistentData().remove("DragonColonies_GuardDeployed");
                    this.assignedDragon.getPersistentData().remove("DragonColonies_GuardUUID");

                    this.assignedDragon.discard();
                    this.assignedDragon = null;
                    this.assignedDragonUUID = null;
                    this.isReturningDragon = false;
                    this.isMountingDragon = false;
                    this.currentReturnTarget = null;
                }
            }
        }
    }

    public void triggerDragonReturn() {
        if (!this.isReturningDragon && this.assignedDragon != null) {
            this.isReturningDragon = true;
            this.assignedDragon.setTarget(null);
            OmniAttackHandler h = (OmniAttackHandler) this.assignedDragon.componentRegistry.get(OmniAttackHandler.class);
            if (h != null && h.isAttacking()) {
                h.handlePlayerFiring(false);
            }
        }
    }

    public UUID getAssignedDragonUUID() {
        return this.assignedDragonUUID;
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
        if (this.assignedDragon != null && this.isReturningDragon && this.worker != null) {
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
            if (!tag.getBoolean("Deployed") && !tag.getBoolean("IsDead")) {
                return true;
            }
        }
        return false;
    }
}