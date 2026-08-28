package net.kb150.survivorcolonies.entity.ai;

import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;

public class SurvivorEatGoal extends Goal {
    private final SurvivorEntity survivor;
    private int eatingTicks = 0;
    private int foodSlot = -1;
    
    private ItemStack previousOffhand = ItemStack.EMPTY;
    private ItemStack foodToEat = ItemStack.EMPTY;

    public SurvivorEatGoal(SurvivorEntity survivor) {
        this.survivor = survivor;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.survivor.getHealth() >= this.survivor.getMaxHealth() 
                || this.survivor.getTarget() != null 
                || this.survivor.isPassenger()) {
            return false;
        }

        if (this.survivor.getRandom().nextInt(100) >= 5) {
            return false;
        }

        this.foodSlot = findFoodSlot();
        return this.foodSlot != -1;
    }

    @Override
    public void start() {
        this.eatingTicks = 32;

        if (this.foodSlot != -1) {
            ItemStack invStack = this.survivor.getInventory().getItem(this.foodSlot);
            if (!invStack.isEmpty()) {
                this.previousOffhand = this.survivor.getItemBySlot(EquipmentSlot.OFFHAND).copy();
                
                this.foodToEat = invStack.copy();
                this.foodToEat.setCount(1);
                this.survivor.setItemSlot(EquipmentSlot.OFFHAND, this.foodToEat);
            }
        }
    }

    @Override
    public void tick() {
        this.eatingTicks--;
        
        if (this.eatingTicks % 4 == 0) {
            this.survivor.playSound(SoundEvents.GENERIC_EAT, 0.5F, 1.0F);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return this.eatingTicks > 0 && !this.foodToEat.isEmpty() && this.survivor.getTarget() == null;
    }

	@Override
    public void stop() {
        if (this.eatingTicks <= 0 && !this.foodToEat.isEmpty()) {
            FoodProperties foodProps = this.foodToEat.getItem().getFoodProperties(this.foodToEat, this.survivor);
            
            float healAmount = 4.0F; // Fallback
            if (foodProps != null) {
                float nutrition = (float) foodProps.getNutrition();
                // Vanilla-Berechnung für die Sättigung
                float saturation = nutrition * foodProps.getSaturationModifier() * 2.0F;
                
                // Kombiniert Nährwert und Sättigung für die finale Heilung (ausbalanciert mit * 0.5)
                healAmount = (nutrition + saturation) * 0.5F; 
            }
            
            this.survivor.heal(healAmount);
            this.survivor.getInventory().removeItem(this.foodSlot, 1);
            
            this.survivor.playSound(SoundEvents.PLAYER_BURP, 0.5F, 1.0F);
        }

        // Urzustand der Zweithand exakt wiederherstellen
        this.survivor.setItemSlot(EquipmentSlot.OFFHAND, this.previousOffhand);

        // Aufräumen
        this.previousOffhand = ItemStack.EMPTY;
        this.foodToEat = ItemStack.EMPTY;
        this.foodSlot = -1;
    }

    private int findFoodSlot() {
        var inv = this.survivor.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.isEdible()) {
                return i;
            }
        }
        return -1;
    }
}