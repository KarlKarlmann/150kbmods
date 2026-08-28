package net.kb150.survivorcolonies.entity.ai;

import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.pathfinder.Path;

import java.util.*;

public class SurvivorScavengeGoal extends Goal {
    private final SurvivorEntity survivor;
    private ItemEntity targetItem;
    
    // Schutz gegen Hängenbleiben & Bouncing
    private int pathTimeout = 0;
    private static final int MAX_PATH_TIME = 120; // Max. 6 Sekunden Versuch pro Item
    
    // Blacklist für Items, die nicht erreicht werden konnten (ItemEntity -> Ablauf-Tick)
    private final Map<ItemEntity, Long> unreachableItems = new WeakHashMap<>();

    public SurvivorScavengeGoal(SurvivorEntity survivor) {
        this.survivor = survivor;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.survivor.isPassenger() 
                || this.survivor.getTarget() != null 
                || this.survivor.getTradingPlayer() != null) {
            return false;
        }

        // 10% Chance pro Tick zum Suchen (spart Performance)
        if (this.survivor.getRandom().nextInt(10) != 0) return false;

        // Veraltete Blacklist-Einträge aufräumen
        long gameTime = this.survivor.level().getGameTime();
        this.unreachableItems.entrySet().removeIf(entry -> gameTime > entry.getValue());

        List<ItemEntity> items = this.survivor.level().getEntitiesOfClass(
            ItemEntity.class,
            this.survivor.getBoundingBox().inflate(10.0D),
            item -> item.isAlive() 
                && !item.getItem().isEmpty() 
                && !this.unreachableItems.containsKey(item)
        );

        if (items.isEmpty()) return false;

        // Nächstgelegenes Item suchen, das einen GÜLTIGEN Pfad hat
        items.sort(Comparator.comparingDouble(this.survivor::distanceToSqr));

        for (ItemEntity item : items) {
            Path path = this.survivor.getNavigation().createPath(item, 0);
            if (path != null && path.canReach()) {
                this.targetItem = item;
                return true;
            } else {
                // Unerreichbar! Direkt für 15 Sekunden (300 Ticks) sperren
                this.unreachableItems.put(item, gameTime + 300);
            }
        }

        return false;
    }

	@Override
    public void start() {
        if (this.targetItem != null) {
            this.pathTimeout = 0;
            // Anstatt zur Entität zu navigieren, zwingen wir ihn auf die exakten X/Y/Z Koordinaten
            this.survivor.getNavigation().moveTo(
                this.targetItem.getX(), 
                this.targetItem.getY(), 
                this.targetItem.getZ(), 
                1.1D
            );
        }
    }

    @Override
    public boolean canContinueToUse() {
        // Hält fest am TargetItem – KEIN Wechsel zu anderen Items erlaubt!
        return this.targetItem != null 
            && this.targetItem.isAlive() 
            && this.pathTimeout < MAX_PATH_TIME
            && this.survivor.getTarget() == null 
            && this.survivor.getTradingPlayer() == null;
    }

	@Override
    public void tick() {
        if (this.targetItem == null) return;

        this.pathTimeout++;
        
        // Lässt den Überlebenden das Item beim Aufsammeln anschauen
        this.survivor.getLookControl().setLookAt(this.targetItem, 30.0F, 30.0F);

        // Radius leicht auf 4.0D (2 Blöcke) erhöht, um die Wipp-Animation von Items auszugleichen
        if (this.survivor.distanceToSqr(this.targetItem) < 4.0D) {
            ItemStack stack = this.targetItem.getItem();
            
            if (!this.survivor.tryEquipBetterItem(stack)) {
                this.survivor.getInventory().addItem(stack);
            }

            this.targetItem.discard();
            this.survivor.playSound(SoundEvents.ITEM_PICKUP, 0.2F, 1.0F);
            this.stop();
        } 
        // Wegfindung alle 10 Ticks (0,5 Sek) aktualisieren, statt auf isDone() zu warten[cite: 4]
        else if (this.pathTimeout % 10 == 0) {
            this.survivor.getNavigation().moveTo(
                this.targetItem.getX(), 
                this.targetItem.getY(), 
                this.targetItem.getZ(), 
                1.1D
            );
        }
    }

    @Override
    public void stop() {
        // Falls das Goal durch Timeout beendet wurde: Item auf Blacklist setzen!
        if (this.pathTimeout >= MAX_PATH_TIME && this.targetItem != null) {
            long gameTime = this.survivor.level().getGameTime();
            this.unreachableItems.put(this.targetItem, gameTime + 300);
        }

        this.survivor.getNavigation().stop();
        this.targetItem = null;
        this.pathTimeout = 0;
    }
}