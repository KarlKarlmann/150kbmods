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
import net.magister.liberanimalium.chassis.component.CommandComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Basis-KI fuer Drachenreiter-Wachen in MineColonies.
 * Verwaltet den Arbeits- und Patrouillenalltag, Hort-Abruf und FAUNA-Mounting.
 */
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

    private List<BlockPos> activeOrbitRing = null;
    private int currentOrbitIndex = 0;
    private BlockPos lastReachedPatrolPoint = null;
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
                new AITarget(AIWorkerState.GUARD_FOLLOW, this::dragonFollow, 1),
                new AITarget(AIWorkerState.START_WORKING, this::handleDragonControl, 1),
                new AITarget(AIWorkerState.INVENTORY_FULL, this::handleDragonControl, 1),
                new AITarget(AIWorkerState.DECIDE, this::handleDragonControl, 1),
                new AITarget(AIWorkerState.NEEDS_ITEM, this::handleDragonControl, 1),
                new AITarget(AIWorkerState.GATHERING_REQUIRED_MATERIALS, this::handleDragonControl, 1),
                new AITarget(AIWorkerState.GET_MATERIALS, this::handleDragonControl, 1),

                new AITarget(AIWorkerState.GUARD_DECIDE, this::handleDragonControl, 1),
                new AITarget(AIWorkerState.GUARD_PATROL, this::patrol, 1),
                new AITarget(AIWorkerState.GUARD_GUARD, this::handleDragonControl, 1),
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

    public IAIState dragonFollow() {
        if (this.buildingGuards == null || !(this.worker.level() instanceof ServerLevel level)) return null;
        if (this.worker.getTarget() != null && this.worker.getTarget().isAlive()) return null;

        BlockPos followPos = this.buildingGuards.getPositionToFollow();
        if (followPos == null) return null;

        DragonBase currentDragon = getAssignedDragon();

        // Fallback ohne Drachen: Regulaeres Folgen zu Fuss
        if (!hasDragonOrCanGetOne() || currentDragon == null) {
            if (com.minecolonies.api.util.BlockPosUtil.getDistance2D(this.worker.blockPosition(), followPos) > 30L) {
                com.minecolonies.core.util.TeleportHelper.teleportCitizen(this.worker, level, followPos);
            } else {
                this.walkToUnSafePos(followPos, this.buildingGuards.isTightGrouping() ? 6 : 15);
            }
            return null;
        }

        // Drache vorhanden, aber Wache noch nicht aufgesessen
        if (this.worker.getVehicle() != currentDragon) {
            this.isMountingDragon = true;
            return null;
        }

        // Distanzpruefung zum Folge-Ziel (>35 Bloecke = Verschuett gegangen)
        double distSq = this.worker.distanceToSqr(followPos.getX() + 0.5D, followPos.getY() + 1.0D, followPos.getZ() + 0.5D);
        if (distSq > 35.0D * 35.0D) {
            teleportDragonAndRider(currentDragon, this.worker, followPos);
            DragonColonies.debug("NAVIGATION", "[FOLLOW-TELEPORT] Drache %s und Wache %s zum Spieler teleportiert (Dist: %.1fm)",
                    currentDragon.getName().getString(), this.worker.getName().getString(), Math.sqrt(distSq));
            return null;
        }

        // Registriert das Folge-Ziel als Luftkorridor
        DragonNavigationHandler.setAirTarget(this.worker.getUUID(), followPos);

        int desiredRange = this.buildingGuards.isTightGrouping() ? 6 : 15;
        this.walkToUnSafePos(followPos, desiredRange);

        return null;
    }

    private void teleportDragonAndRider(DragonBase dragon, AbstractEntityCitizen citizen, BlockPos targetPos) {
        double x = targetPos.getX() + 0.5D;
        double y = targetPos.getY() + 1.0D;
        double z = targetPos.getZ() + 0.5D;

        dragon.teleportTo(x, y, z);
        if (citizen.getVehicle() != dragon) {
            citizen.moveTo(x, y, z);
            citizen.startRiding(dragon, true);
        }

        dragon.applyMove(null);
        citizen.getNavigation().stop();
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

    public BlockPos getCurrentPatrolPoint() {
        return this.currentPatrolPoint;
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
                this.flightController.handleFlight(
                        this.worker,
                        currentDragon,
                        this.building,
                        this.radarTickCounter,
                        this.currentPatrolPoint,
                        !hasAxe() || isReturningDragon
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

        // Drache muss zurueck in den Hort, falls verletzt, hungrig oder entfremdet (isEstranged)
        boolean dragonNeedsReturn = currentDragon != null && currentDragon.isAlive() && !DragonStatusHelper.isFitToFly(currentDragon);

        if (currentDragon != null && (!workerHasAxe || dragonNeedsReturn)) {
            if (!this.isReturningDragon) {
                this.isReturningDragon = true;
                currentDragon.setTarget(null);
                currentDragon.dropTrigger();
                DragonColonies.debug("NAVIGATION", "[RETURN-TRIGGER] Rueckruf eingeleitet | WacheAxt: {} | FitToFly: {}",
                        workerHasAxe, !dragonNeedsReturn);
            }
        }

        // Solange der Drache auf dem Heimweg ist, füttern wir nur die Hort-Navigation
        if (this.isReturningDragon && currentDragon != null) {
            handleReturnDragonToRoost(level);
            return null;
        }

        return super.decide();
    }

    @Override
    public IAIState patrol() {
        if (this.buildingGuards == null || !(this.worker.level() instanceof ServerLevel level)) return super.patrol();

        DragonBase currentDragon = getAssignedDragon();
        if (!hasDragonOrCanGetOne()) return super.patrol();
        if (this.worker.getVehicle() != currentDragon) {
            this.isMountingDragon = true;
            return null;
        }

        // Bei Feindkontakt pausiert die Patrouille sofort (wie im Original 1.3),
        // damit die Patrouillenrouten nicht gegen die Kampf-Positionierung ankaempfen!
        if (this.worker.getTarget() != null && this.worker.getTarget().isAlive()) {
            return null;
        }

        BlockPos rawTarget = this.buildingGuards.getNextPatrolTarget(false);
        if (rawTarget == null) return null;

        // Fall A: Wache haelt die Stellung am Posten und kreist im 8-Punkte-Orbit
        if (rawTarget.equals(this.lastReachedPatrolPoint)) {
            if (this.activeOrbitRing == null || this.activeOrbitRing.isEmpty()) {
                this.activeOrbitRing = generateOrbitRing(rawTarget, 16, 12);
                DragonNavigationHandler.registerAirRoute(this.worker.getUUID(), this.activeOrbitRing);
                this.currentOrbitIndex = 0;
            }

            // Weiterschalten zum naechsten Ringpunkt erst, wenn der vorherige passiert wurde
            if (this.currentPatrolPoint == null || this.walkToSafePos(this.currentPatrolPoint)) {
                this.currentPatrolPoint = this.activeOrbitRing.get(this.currentOrbitIndex);
                this.currentOrbitIndex = (this.currentOrbitIndex + 1) % this.activeOrbitRing.size();
                this.walkToSafePos(this.currentPatrolPoint);
            }
            return null;
        }

        // Fall B: Neuer Wachposten zugewiesen -> Route initialisieren und Erstanflug starten
        this.activeOrbitRing = null;
        DragonNavigationHandler.clearAirRoute(this.worker.getUUID());

        this.currentPatrolPoint = (this.building instanceof BuildingDragonRoost roost && roost.getStorageModule() != null)
                ? roost.getStorageModule().getSafeAirTarget(level, rawTarget)
                : DragonNavigationHandler.getHighAirPos(level, rawTarget);

        // Registriert Erstanflug PLUS den zukuenftigen Orbitring vorab
        List<BlockPos> fullRoute = new ArrayList<>();
        fullRoute.add(this.currentPatrolPoint);
        fullRoute.addAll(generateOrbitRing(rawTarget, 16, 12));
        DragonNavigationHandler.registerAirRoute(this.worker.getUUID(), fullRoute);

        // Exakt ein Navigationsaufruf pro Zyklus. Wenn erreicht, Vollzug melden; der Orbit startet im Folgetick.
        if (this.walkToSafePos(this.currentPatrolPoint)) {
            this.lastReachedPatrolPoint = rawTarget;
            this.buildingGuards.arrivedAtPatrolPoint(this.worker);
        }

        return null;
    }

    private List<BlockPos> generateOrbitRing(BlockPos center, int radius, int yOffset) {
        List<BlockPos> ring = new ArrayList<>(8);
        for (int i = 0; i < 8; i++) {
            double angle = i * (Math.PI / 4.0); // 45-Grad-Schritte fuer 8 Segmente
            int x = center.getX() + (int) Math.round(radius * Math.cos(angle));
            int z = center.getZ() + (int) Math.round(radius * Math.sin(angle));
            ring.add(new BlockPos(x, center.getY() + yOffset, z));
        }
        return ring;
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

            if (this.mountingTicks % 10 == 0 || this.mountingTicks == 1) {
                DragonColonies.debug("NAVIGATION", "[MOUNT-PHASE] Tick: {} | DistSqr: {} | CitizenPos: {} | DragonPos: {}",
                        this.mountingTicks, String.format("%.2f", distSqr), citizen.blockPosition().toShortString(), currentDragon.blockPosition().toShortString());
            }

            // Bringt den Drachen am Boden ueber die regulaere Pfadfindung heran
            if (distSqr > 16.0D && this.mountingTicks % 20 == 1) {
                currentDragon.getNavigation().moveTo(citizen, 1.25D);
            }

            // Aufsitz-Distanz erreicht: Sprung-Animation und startRiding
            if (distSqr <= 16.0D) {
                Vec3 startPos = citizen.position();
                Vec3 targetPos = new Vec3(currentDragon.getX(), currentDragon.getY() + 0.5D, currentDragon.getZ());

                triggerMountLeapEffect(citizen, startPos, targetPos);

                boolean mounted = citizen.startRiding(currentDragon, true);
                if (mounted) {
                    this.isMountingDragon = false;
                    this.mountingTicks = 0;
                    currentDragon.applyMove(null);
                    currentDragon.getNavigation().stop();

                    CommandComponent cmd = (CommandComponent) currentDragon.componentRegistry.get(CommandComponent.class);
                    if (cmd != null) {
                        cmd.wakeFromRest();
                    }
                    currentDragon.getPersistentData().putUUID("DragonColonies_GuardUUID", citizen.getUUID());
                    return;
                }
            }

            // Timeout-Schutz: Bei Feststecken direkter Zwangstransfer
            if (this.mountingTicks >= MAX_MOUNT_TICKS || citizen.getNavigation().isStuck()) {
                Vec3 startPos = citizen.position();
                Vec3 targetPos = new Vec3(currentDragon.getX(), currentDragon.getY() + 0.5D, currentDragon.getZ());

                DragonColonies.debug("NAVIGATION", "[MOUNT-FORCE] Timeout erreicht (Ticks: {}) -> Teleportiere Citizen zu Drache", this.mountingTicks);

                triggerMountLeapEffect(citizen, startPos, targetPos);
                citizen.moveTo(targetPos.x, targetPos.y, targetPos.z);
                boolean mounted = citizen.startRiding(currentDragon, true);

                if (mounted) {
                    this.isMountingDragon = false;
                    this.mountingTicks = 0;
                    currentDragon.applyMove(null);
                    currentDragon.getNavigation().stop();

                    CommandComponent cmd = (CommandComponent) currentDragon.componentRegistry.get(CommandComponent.class);
                    if (cmd != null) {
                        cmd.wakeFromRest();
                    }
                    currentDragon.getPersistentData().putUUID("DragonColonies_GuardUUID", citizen.getUUID());
                }
            }
        }
    }

    public IAIState handleDragonControl() {
        if (this.worker != null && getAssignedDragon() != null) {
            RIDERS_BY_CITIZEN.put(this.worker.getUUID(), this);
        }
        // Verhindert Deadlock im Zustand NO_TARGET / GUARD_GUARD: Gibt die Kontrolle an decide() zurueck,
        // damit Hunger, Schichtwechsel und Patrouillen-Updates ausgewertet werden koennen.
        return this.decide();
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

            if (fit && DragonStorageModule.MODE_ASSIGNED.equals(mode) && assignedId == workerCitizenId) {
                targetDragonNbt = tag;
                DragonColonies.debug("AI", "[RETRIEVE-MATCH] Passender zugewiesener Drache gefunden (Index {})", i);
                break;
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

        this.activeOrbitRing = null;
        this.currentOrbitIndex = 0;
        DragonNavigationHandler.clearAirRoute(this.worker.getUUID());

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
            currentDragon.dropTrigger(); // Bricht alle aktiven Atem-/Fernkampfangriffe in FAUNA ab
            this.activeOrbitRing = null;
            this.currentOrbitIndex = 0;
            DragonNavigationHandler.clearAirRoute(this.worker.getUUID());
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
    public void resetAI() {
        super.resetAI();
        this.activeOrbitRing = null;
        this.currentOrbitIndex = 0;
        if (this.worker != null) {
            DragonNavigationHandler.clearAirRoute(this.worker.getUUID());
        }
    }

    @Override
    public void onRemoval() {
        super.onRemoval();
        this.activeOrbitRing = null;
        this.currentOrbitIndex = 0;
        if (this.worker != null) {
            UUID id = this.worker.getUUID();
            DragonNavigationHandler.clearAirRoute(id);
            RIDERS_BY_CITIZEN.remove(id);
        }
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