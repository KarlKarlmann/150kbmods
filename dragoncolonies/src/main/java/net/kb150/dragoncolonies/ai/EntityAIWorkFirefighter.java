package net.kb150.dragoncolonies.ai;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import net.kb150.dragoncolonies.buildings.BuildingFireStation;
import net.kb150.dragoncolonies.jobs.JobFirefighter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.NotNull;

/**
 * Architekturbericht Punkt 5.2.2: Zustandsautomat und Brandbekämpfung
 * Diese KI steuert den Feuerwehrmann. Er reagiert auf Brand-Alarme (geliefert durch das FirefighterDispatchModule der Feuerwache),
 * rennt zum Feuer, löscht es mit Wasser und fordert neues Wasser an, wenn seine Eimer leer sind.
 */
public class EntityAIWorkFirefighter extends AbstractEntityAIBasic<JobFirefighter, BuildingFireStation> {

    // Eigene States für den Feuerwehrmann (Erweitert konzeptionell die Minecolonies States)
    private enum FirefighterState {
        IDLE,               // Wartet auf Alarm
        FETCHING_WATER,     // Holt Wasser aus dem Gebäude/fordert es an
        EMERGENCY_RESPONSE, // Rennt zum Feuer
        WORKING             // Löscht das Feuer
    }

    private FirefighterState currentState = FirefighterState.IDLE;
    private BlockPos currentFireTarget = null;
    
    // Zähler, damit nicht jeden Tick eine Pfadfindung läuft (Performance!)
    private int pathfindingCooldown = 0;

    public EntityAIWorkFirefighter(@NotNull JobFirefighter job) {
        super(job);
        
        // Klinkt die Logik in die Minecolonies 1.20.1 State Machine ein
        this.registerTargets(new AITarget(AIWorkerState.IDLE, this::processWork, 10));
        this.registerTargets(new AITarget(AIWorkerState.START_WORKING, this::processWork, 10));
    }

    @Override
    public Class<BuildingFireStation> getExpectedBuildingClass() {
        return BuildingFireStation.class;
    }

    /**
     * Die zentrale Methode für unseren Workflow, die von den Minecolonies States aufgerufen wird.
     * Ein Rückgabewert von 'null' bedeutet: Bleibe in diesem State und rufe die Methode erneut auf.
     */
    private IAIState processWork() {
        if (this.worker == null || this.worker.isDeadOrDying()) return AIWorkerState.IDLE;

        Level level = this.worker.level();
        if (level.isClientSide) return AIWorkerState.IDLE;

        if (pathfindingCooldown > 0) {
            pathfindingCooldown--;
        }

        switch (currentState) {
            case IDLE:
                return handleIdleState();
            case FETCHING_WATER:
                return handleFetchingWaterState();
            case EMERGENCY_RESPONSE:
                return handleEmergencyResponseState(level);
            case WORKING:
                return handleWorkingState(level);
        }
        
        return null;
    }
    
    /**
     * Wird von außen (z.B. der Feuerwache) aufgerufen, wenn ein Feuer entdeckt wurde.
     */
    public void triggerAlarm(BlockPos fireLocation) {
        // Nimmt das neueste Feuer an, wenn er Idle ist.
        if (this.currentState == FirefighterState.IDLE) {
            this.currentFireTarget = fireLocation;
            this.currentState = FirefighterState.EMERGENCY_RESPONSE;
        }
    }

    private IAIState handleIdleState() {
        // Architekturbericht Punkt 5.2.1: Integration in das Anforderungssystem
        int waterBuckets = InventoryUtils.getItemCountInItemHandler(this.worker.getInventoryCitizen(), stack -> stack.is(Items.WATER_BUCKET));
        
        if (waterBuckets < 2) {
            this.currentState = FirefighterState.FETCHING_WATER;
            return null;
        }
        
        // Wenn idle und Wasser voll, bewege dich zurück zur Feuerwache.
        if (this.building != null) {
            if (pathfindingCooldown <= 0) {
                // Nutze getPosition() statt getLocation(), da letzteres eine ILocation zurückgibt
                this.walkToWorkPos(this.building.getPosition());
                pathfindingCooldown = 40;
            }
        }
        return null;
    }

    private IAIState handleFetchingWaterState() {
        ICitizenData citizenData = this.worker.getCitizenData();
        IRequestManager requestManager = this.worker.getCitizenColonyHandler().getColonyOrRegister().getRequestManager();
        
        int waterBuckets = InventoryUtils.getItemCountInItemHandler(this.worker.getInventoryCitizen(), stack -> stack.is(Items.WATER_BUCKET));
        if (waterBuckets >= 2) {
             this.currentState = FirefighterState.IDLE;
        } else {
             // TODO: Request API korrekt für Wasser implementieren
             // Fürs erste wartet er einfach hier, bis ihm jemand (Spieler oder Courier) Wasser gibt.
        }
        return null;
    }

    private IAIState handleEmergencyResponseState(Level level) {
        if (currentFireTarget == null) {
            this.currentState = FirefighterState.IDLE;
            return null;
        }
        
        double distance = this.worker.distanceToSqr(currentFireTarget.getX(), currentFireTarget.getY(), currentFireTarget.getZ());
        
        // Wenn nah genug am Feuer (ca. 3 Blöcke) -> Lösch-Modus!
        if (distance < 9.0) {
            this.worker.getNavigation().stop(); // Stehenbleiben
            this.currentState = FirefighterState.WORKING;
            return null;
        }

        // Hinrennen!
        if (pathfindingCooldown <= 0) {
             // 1.5D ist schnelles Rennen (Sprinten).
             boolean pathSuccess = this.walkToUnSafePos(currentFireTarget, 1);
             if (!pathSuccess) {
                 // Kein Weg gefunden? Evtl blockiert. Abbruch.
                 this.currentFireTarget = null;
                 this.currentState = FirefighterState.IDLE;
             }
             pathfindingCooldown = 20; 
        }
        return null;
    }

    private IAIState handleWorkingState(Level level) {
        if (currentFireTarget == null) {
            this.currentState = FirefighterState.IDLE;
            return null;
        }

        // Architekturbericht Punkt 5.2.2: Lösch-Logik
        BlockState state = level.getBlockState(currentFireTarget);
        
        if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
            // 1. Besitzt er noch einen vollen Wassereimer?
            int waterBucketSlot = InventoryUtils.findFirstSlotInItemHandlerWith(this.worker.getInventoryCitizen(), stack -> stack.is(Items.WATER_BUCKET));
            
            if (waterBucketSlot != -1) {
                // Löschen! Setze Block zu Luft.
                level.setBlock(currentFireTarget, Blocks.AIR.defaultBlockState(), 3);
                
                // Visuelles Feedback
                level.playSound(null, currentFireTarget, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.addParticle(ParticleTypes.CLOUD, currentFireTarget.getX() + 0.5, currentFireTarget.getY() + 0.5, currentFireTarget.getZ() + 0.5, 0, 0.1, 0);

                // Inventar anpassen: WATER_BUCKET extrahieren, leeren BUCKET einfügen
                this.worker.getInventoryCitizen().extractItem(waterBucketSlot, 1, false);
                // Sicheres Einfügen über Forge ItemHandlerHelper statt Minecolonies-interner API
                ItemHandlerHelper.insertItem(this.worker.getInventoryCitizen(), new ItemStack(Items.BUCKET), false);
                
                // TODO: Dem STATS_MODULE der Feuerwache melden, dass ein Feuer gelöscht wurde.
            } else {
                 // Kein Wasser mehr! Alarm abbrechen und neues Wasser holen.
                 this.currentState = FirefighterState.FETCHING_WATER;
                 return null;
            }
        }
        
        // Feuer ist aus (oder war gar nicht da/wurde schon gelöscht)
        this.currentFireTarget = null;
        this.currentState = FirefighterState.IDLE;
        return null;
    }
}