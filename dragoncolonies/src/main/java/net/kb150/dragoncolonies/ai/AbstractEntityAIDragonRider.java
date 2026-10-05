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
import net.kb150.dragoncolonies.buildings.BuildingDragonRoost;
import net.kb150.dragoncolonies.buildings.modules.DragonStorageModule;
import net.kb150.dragoncolonies.network.DragonColoniesNetwork;
import net.kb150.dragoncolonies.network.message.RiderLeapMessage;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.bookofdragons.entity.component.ranged.OmniAttackHandler;
import net.magister.bookofdragons.entity.state.GroundStance;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.UUID;

public abstract class AbstractEntityAIDragonRider<J extends AbstractJobGuard<J>, B extends AbstractBuildingGuards> extends AbstractEntityAIGuard<J, B> {

    protected DragonBase assignedDragon = null;
    protected boolean isMountingDragon = false;
    protected boolean isReturningDragon = false;
    protected int retrievedCooldownTicks = 0;
    protected int mountingTicks = 0;
    private static final int MAX_MOUNT_TICKS = 60; // 3 Sekunden Gnadenfrist
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

// ... existing code ...
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
        // Standard in MineColonies-Guards ist 5 * Stufe (bereits nach 10-15s erreicht).
        // Wir erhöhen den Schwellenwert auf ausgiebige Patrouillenzeiten (z. B. 120 Aktionen).
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

        // 1. Wenn die Taschen randvoll sind (z. B. durch aufgesammelten Mob-Loot), muss geleert werden
        if (this.worker.getCitizenInventoryHandler().isInventoryFull()) {
            return true;
        }

        // 2. Timer-Schwelle erreicht: Nur landen und zur Kiste laufen, wenn auch wirklich Loot existiert!
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
                // Rüstung und Waffen werden nicht als abzugebender "Müll/Loot" gewertet
                if (!(stack.getItem() instanceof net.minecraft.world.item.ArmorItem)
                        && !(stack.getItem() instanceof net.minecraft.world.item.TieredItem)) {
                    return true;
                }
            }
        }
        // Inventar ist leer oder enthält nur persönliche Ausrüstung -> kein Blasendruck, weiterfliegen!
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

        // Wenn der Drache existiert, Dienst aktiv ist und wir nicht aufsitzen -> Aufsitzen erzwingen!
        if (shouldWork && currentDragon != null && currentDragon.isAlive() && !isReturningDragon && this.worker.getVehicle() != currentDragon) {
            this.isMountingDragon = true;
        }

        if (isMountingDragon) {
            handleMountingPhase(this.worker);
        }

        boolean dragonNeedsReturn = false;
        if (currentDragon != null && currentDragon.isAlive()) {
            if (isDragonHungry(currentDragon) || isDragonInjured(currentDragon)) {
                dragonNeedsReturn = true;
            }
        }

        if ((!workerHasAxe || dragonNeedsReturn) && currentDragon != null) {
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

    private boolean isDragonHungry(DragonBase dragon) {
        if (dragon == null || dragon.getNeedsSystem() == null) return false;
        return dragon.getNeedsSystem().getFoodLevel() < 60;
    }

    private boolean isDragonInjured(DragonBase dragon) {
        if (dragon == null) return false;
        return dragon.getHealth() < (dragon.getMaxHealth() - 10.0f);
    }

    @Override
    public IAIState patrol() {
        if (this.buildingGuards == null || !(this.worker.level() instanceof ServerLevel level)) {
            return super.patrol();
        }

        // 1. KAMPF-ABBRUCH: Hat die Wache Feinde im Visier, pausiert die Patrouille sofort vollständig!
        // Verhindert den fatalen Konflikt, bei dem Patrouille und Kampf zeitgleich Flugpunkte in den Drachen hämmern.
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

        // 2. ZU FUSS: Hat die Wache keinen aktiven Drachen, übernimmt MineColonies vollständig die Bodenpatrouille.
        if (!hasDragon) {
            return super.patrol();
        }

        // 3. MOUNT-PRIORITÄT: Ein Drachenreiter läuft niemals zu Fuß unter seinem Luftziel her.
        if (!isReturningDragon && this.worker.getVehicle() != currentDragon) {
            this.isMountingDragon = true;
            handleMountingPhase(this.worker);
            return null;
        }

        // 4. FLUGPATROUILLE: Wache sitzt im Sattel und steuert rein dreidimensionale Luftziele an.
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
                // Loitering: Am erreichten Wegpunkt kreist der Drache im Luftraum mit ausreichend Flugzeit
                if (rawTarget.equals(this.lastReachedPatrolPoint)) {
                    this.loiterAngle += 0.35F;
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
                    // Mindestens 30-40 Ticks Flugzeit geben, damit der Drache den Bogen fliegen kann statt jeden Tick überschrieben zu werden
                    this.setCurrentDelay(40);

                } else {
                    this.currentPatrolPoint = DragonNavigationHandler.getHighAirPos(level, rawTarget);

                    if (this.walkToSafePos(this.currentPatrolPoint)) {
                        this.setCurrentDelay(hasDragon ? 20 : 0);
                        this.lastReachedPatrolPoint = rawTarget;
                        this.buildingGuards.arrivedAtPatrolPoint(this.worker);

                        // Geländehöhe im 35-Block-Kreis scannen, um Berg- und Baumkollisionen beim Kreisen zu verhindern
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

            // 1. DYNAMISCHES AUFSAMMELN: Drache schließt aktiv mit Tempo auf den Reiter auf
            if (currentDragon.getNavigation() != null && !currentDragon.getNavigation().isInProgress()) {
                currentDragon.getNavigation().moveTo(citizen, 1.35D);
            }

            // 2. FLIEGENDER PICKUP (Reichweite <= 4.0 Blöcke)
            // 2. Regulärer dynamischer Pickup im Vorbeigehen (Reichweite <= 4.0 Blöcke)
            if (distSqr <= 16.0D) {
                Vec3 startPos = citizen.position();
                Vec3 targetPos = new Vec3(currentDragon.getX(), currentDragon.getY() + 0.5D, currentDragon.getZ());

                // Zum Testen: Effekt auch beim normalen Aufsteigen direkt abfeuern
                triggerMountLeapEffect(citizen, startPos, targetPos);

                boolean mounted = citizen.startRiding(currentDragon, true);
                if (mounted) {
                    this.isMountingDragon = false;
                    this.mountingTicks = 0;
                    currentDragon.setCommand(0);
                    currentDragon.setGroundStance(GroundStance.IDLE);
                    currentDragon.getPersistentData().putUUID("DragonColonies_GuardUUID", citizen.getUUID());
                    return;
                }
            }

            // 3. DER HAMMER: Timeout nach 3 Sekunden oder Navigation komplett festgefahren
            if (this.mountingTicks >= MAX_MOUNT_TICKS || citizen.getNavigation().isStuck()) {
                Vec3 startPos = citizen.position();
                Vec3 targetPos = new Vec3(currentDragon.getX(), currentDragon.getY() + 0.5D, currentDragon.getZ());

                // Clientseitigen Phantom-Dash via Netzwerk triggern
                triggerMountLeapEffect(citizen, startPos, targetPos);

                // Physikalisch sofort aufsatteln
                citizen.moveTo(targetPos.x, targetPos.y, targetPos.z);
                boolean mounted = citizen.startRiding(currentDragon, true);
                if (mounted) {
                    isMountingDragon = false;
                    mountingTicks = 0;
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
	
    private boolean tryRetrieveDragonFromRoost(ServerLevel level) {
        if (!(this.building instanceof BuildingDragonRoost roost)) return false;

        DragonStorageModule storage = roost.getStorageModule();
        if (storage == null) return false;

        List<CompoundTag> stored = storage.getStoredDragons();
        if (stored == null || stored.isEmpty()) return false;

        int workerCitizenId = this.worker.getCitizenData() != null ? this.worker.getCitizenData().getId() : -1;
        CompoundTag targetDragonNbt = null;

        // 1. PRIORITAET: Feste Zuweisung an genau diese Wache
        for (CompoundTag tag : stored) {
            if (isEligibleForFlight(tag)) {
                String mode = tag.getString(DragonStorageModule.TAG_ASSIGNMENT_MODE);
                int assignedId = tag.getInt(DragonStorageModule.TAG_ASSIGNED_CITIZEN_ID);

                if (DragonStorageModule.MODE_ASSIGNED.equals(mode) && assignedId == workerCitizenId) {
                    targetDragonNbt = tag;
                    break;
                }
            }
        }

        // 2. PRIORITAET: Freier Pool (AUTO) - Nur wenn keine fremde Bindung oder Sperre vorliegt
        if (targetDragonNbt == null) {
            for (CompoundTag tag : stored) {
                if (isEligibleForFlight(tag)) {
                    String mode = tag.getString(DragonStorageModule.TAG_ASSIGNMENT_MODE);
                    
                    // Ein Drache mit Modus LOCKED, BREEDING oder ASSIGNED an andere darf niemals automatisch genommen werden
                    boolean isAuto = mode.isEmpty() || DragonStorageModule.MODE_AUTO.equals(mode);
                    if (isAuto) {
                        targetDragonNbt = tag;
                        break;
                    }
                }
            }
        }

        if (targetDragonNbt == null) return false;

        UUID roostDragonId = targetDragonNbt.hasUUID(DragonStorageModule.TAG_ROOST_DRAGON_ID)
                ? targetDragonNbt.getUUID(DragonStorageModule.TAG_ROOST_DRAGON_ID)
                : UUID.randomUUID();

        // Zentraler Lifecycle-Aufruf: Erzeugt Entity-UUID, setzt ActiveEntityUUID und stempelt PersistentData
        DragonBase dragon = storage.deployDragon(roostDragonId, level, roost.getPosition(), this.worker.getUUID());
        if (dragon != null) {
            this.assignedDragon = dragon;
            setAssignedDragonUUID(dragon.getUUID());
            this.isReturningDragon = false;
            this.isMountingDragon = true;
            return true;
        }

        return false;
    }

    private boolean isEligibleForFlight(CompoundTag tag) {
        return !tag.getBoolean(DragonStorageModule.TAG_DEPLOYED)
                && !tag.getBoolean(DragonStorageModule.TAG_IS_DEAD)
                && !tag.getBoolean("DragonColonies_IsEgg")
                && isDragonFit(tag);
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
                // Zentrales Einlagern und Entwerten über DragonStorageModule
                if (storage != null && storage.storeDragon(currentDragon)) {
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
     * Zucht-Drachen, Eier, Babys UND UNSATTELE DRATIONEN WERDEN ABGELEHNT!
     */
    private boolean isDragonFit(CompoundTag dragonTag) {
        if (dragonTag == null) return false;
        
        // 1. Zucht / Ei Sicherheits-Filter
        if (dragonTag.getBoolean("DragonColonies_IsEgg")) return false;
        if (dragonTag.getBoolean("DragonColonies_AllowBreeding")) return false;
        
        // 2. Muss mindestens Broad Wing (Stage 2) sein
        if (dragonTag.contains("GrowthStage") && dragonTag.getInt("GrowthStage") < 2) return false;
        
        // 3. Sättigung prüfen
        if (dragonTag.contains("dragonNeeds")) {
            CompoundTag needs = dragonTag.getCompound("dragonNeeds");
            if (needs.contains("foodLevel") && needs.getInt("foodLevel") < 60) {
                return false;
            }
        }

        // 4. SATTEL-PRÜFUNG: Drache muss gesattelt sein!
        return isDragonSaddledInNbt(dragonTag);
    }

    /**
     * Hilfsmethode: Prüft im NBT des gespeicherten Drachens, ob ein Sattel ausgerüstet ist.
     */
	public static boolean isDragonSaddledInNbt(CompoundTag dragonTag) {
		if (dragonTag == null) return false;

		// 1. Direct synched entity data check (falls die Entity live aus dem RAM gespeichert wurde)
		if (dragonTag.getBoolean("isSaddled") || dragonTag.getBoolean("IsSaddled") || dragonTag.getBoolean("Saddle")) {
			return true;
		}

		// 2. Echtes Book of Dragons NBT-Inventar prüfen (Slot 0 in der "Inventory"-Liste)
		if (dragonTag.contains("Inventory", net.minecraft.nbt.Tag.TAG_LIST)) {
			net.minecraft.nbt.ListTag invList = dragonTag.getList("Inventory", net.minecraft.nbt.Tag.TAG_COMPOUND);
			for (int i = 0; i < invList.size(); i++) {
				CompoundTag itemTag = invList.getCompound(i);
				if (itemTag.getByte("Slot") == 0) {
					String id = itemTag.getString("id");
					return id.equals("minecraft:saddle") || id.endsWith(":saddle");
				}
			}
		}

		return false;
	}
}