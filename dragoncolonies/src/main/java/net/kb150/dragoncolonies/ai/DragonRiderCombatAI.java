package net.kb150.dragoncolonies.ai;

import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.ITickRateStateMachine;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.api.research.util.ResearchConstants;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.constant.ColonyConstants;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.entity.ai.workers.guard.KnightCombatAI;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.util.citizenutils.CitizenItemUtils;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public class DragonRiderCombatAI extends KnightCombatAI {

    private final AbstractEntityAIDragonRider<?, ?> riderAI;

    public DragonRiderCombatAI(EntityCitizen owner, ITickRateStateMachine stateMachine, AbstractEntityAIDragonRider<?, ?> parentAI) {
        super(owner, stateMachine, parentAI);
        this.riderAI = parentAI;
    }

    private boolean isDragonPriority() {
        if (this.riderAI != null && this.riderAI.assignedDragon == null && this.riderAI.isDragonAvailableInRoost()) {
            return true;
        }
        return false;
    }

    @Override
    protected boolean searchNearbyTarget() {
        if (isDragonPriority()) {
            return false;
        }
        return super.searchNearbyTarget();
    }

    @Override
    protected boolean checkForTarget() {
        if (isDragonPriority()) {
            this.target = null;
            this.user.setTarget(null);
            return false; 
        }
        return super.checkForTarget();
    }

    @Override
    public boolean canAttack() {
        if (isDragonPriority()) {
            return false;
        }

        EntityCitizen citizen = (EntityCitizen) this.user;
        int weaponSlot = InventoryUtils.getFirstSlotOfItemHandlerContainingEquipment(
                citizen.getInventoryCitizen(), 
                (EquipmentTypeEntry) ModEquipmentTypes.axe.get(), 
                0, 
                citizen.getCitizenData().getWorkBuilding().getMaxEquipmentLevel()
        );

        if (weaponSlot != -1) {
            CitizenItemUtils.setHeldItem(citizen, InteractionHand.MAIN_HAND, weaponSlot);
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected double getAttackDamage() {
        double addDmg = 0.0D;
        EntityCitizen citizen = (EntityCitizen) this.user;
        ItemStack heldItem = citizen.getItemInHand(InteractionHand.MAIN_HAND);

        if (ItemStackUtils.doesItemServeAsWeapon(heldItem)) {
            if (heldItem.getItem() instanceof AxeItem axeItem) {
                addDmg += axeItem.getAttackDamage() + 3.0F;
            } else if (heldItem.getItem() instanceof SwordItem swordItem) {
                addDmg += swordItem.getDamage() + 3.0F;
            }

            if (this.target != null) {
                addDmg += (double) EnchantmentHelper.getDamageBonus(heldItem, this.target.getMobType()) / 2.5F;
            }
        }

        addDmg += citizen.getCitizenColonyHandler().getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(ResearchConstants.MELEE_DAMAGE);
        if ((double) citizen.getHealth() <= (double) citizen.getMaxHealth() * 0.2) {
            addDmg *= 2.0F;
        }

        if (ColonyConstants.rand.nextDouble() > 1.0F / (1.0F + citizen.getCitizenColonyHandler().getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(ResearchConstants.GUARD_CRIT))) {
            addDmg *= 1.5F;
            if (this.target != null && citizen.level() instanceof ServerLevel serverLevel) {
                serverLevel.getChunkSource().broadcast(citizen, new ClientboundAnimatePacket(this.target, 4));
            }
        }

        return addDmg * (Double) MineColonies.getConfig().getServer().guardDamageMultiplier.get();
    }
}