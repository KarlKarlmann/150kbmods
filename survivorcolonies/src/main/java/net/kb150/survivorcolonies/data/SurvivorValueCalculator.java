package net.kb150.survivorcolonies.data;

import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraftforge.common.ForgeHooks;

public class SurvivorValueCalculator {

    // Wert eines Items, wenn der Überlebende es vom Spieler KAUFT
    public static int getItemValue(ItemStack stack, SurvivorEntity survivor) {
        if (stack.isEmpty()) return 0;

        // 1. Feste Handelswaren (Erze & Währungen)
        if (stack.is(Items.EMERALD)) return 1;
        if (stack.is(Items.IRON_INGOT) || stack.is(Items.RAW_IRON) || 
            stack.is(Items.GOLD_INGOT) || stack.is(Items.RAW_GOLD)) return 5;
        if (stack.is(Items.DIAMOND)) return 30;

        // 2. FEUER (Brenndauer auslesen)
        int burnTime = ForgeHooks.getBurnTime(stack, null);
        if (burnTime > 0) {
            return Math.max(1, burnTime / 200);
        }

        // 3. ESSEN (Nährwert auslesen)
        if (stack.isEdible() && stack.getItem().getFoodProperties(stack, survivor) != null) {
            var food = stack.getItem().getFoodProperties(stack, survivor);
            return food.getNutrition() * 2;
        }

        // 4. STAHL (Ausrüstungs-Upgrade)
        if (stack.getItem() instanceof ArmorItem newArmor) {
            EquipmentSlot slot = newArmor.getEquipmentSlot();
            ItemStack currentStack = survivor.getItemBySlot(slot);
            int currentDefense = (currentStack.getItem() instanceof ArmorItem currentArmor) ? currentArmor.getDefense() : 0;
            int newDefense = newArmor.getDefense();
            if (newDefense > currentDefense) return (newDefense - currentDefense) * 10;
        }

        if (stack.getItem() instanceof SwordItem newSword) {
            ItemStack currentWeapon = survivor.getMainHandItem();
            float currentDmg = (currentWeapon.getItem() instanceof SwordItem currentSword) ? currentSword.getDamage() : 0;
            float newDmg = newSword.getDamage();
            if (newDmg > currentDmg) return Math.round((newDmg - currentDmg) * 8);
        }

        // Fallback: 1 Wertpunkt für sonstigen Kram
        return 1;
    }

    // Wert eines Items, das der Überlebende dem Spieler VERKAUFT (mit Zufallsfaktor)
    public static int getSurvivorSaleValue(ItemStack stack, SurvivorEntity survivor) {
        int baseValue = getItemValue(stack, survivor);
        
        // Schwankt zufällig zwischen 80% und 120% des Basiswerts
        float randomFactor = 0.8f + (survivor.getRandom().nextFloat() * 0.4f);
        return Math.max(1, Math.round(baseValue * randomFactor));
    }
}