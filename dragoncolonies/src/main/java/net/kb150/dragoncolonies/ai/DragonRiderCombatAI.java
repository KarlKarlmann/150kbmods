package net.kb150.dragoncolonies.ai;

import com.minecolonies.api.entity.ai.combat.CombatAIStates;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.ITickRateStateMachine;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickingTransition;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.api.research.util.ResearchConstants;
import com.minecolonies.api.util.DamageSourceKeys;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.SoundUtils;
import com.minecolonies.api.util.constant.ColonyConstants;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.BuildingStatisticsModule;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.entity.ai.BehaviourStateGroup;
import com.minecolonies.core.entity.ai.combat.AttackMoveAI;
import com.minecolonies.core.entity.ai.combat.CombatUtils;
import com.minecolonies.core.entity.ai.workers.guard.AbstractEntityAIGuard;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import com.minecolonies.core.entity.pathfinding.pathresults.PathResult;
import com.minecolonies.core.util.citizenutils.CitizenItemUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Nahkampf-KI für Drachenreiter am Boden.
 * Erbt direkt von AttackMoveAI, um Versions-Inkompatibilitäten zwischen
 * KnightCombatAI (alt) und MeleeCombatAI (neu) in MineColonies vollständig zu eliminieren,
 * implementiert jedoch alle unverzichtbaren Guard- und Kolonie-Mechaniken.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class DragonRiderCombatAI extends AttackMoveAI<EntityCitizen> {

    private final AbstractEntityAIDragonRider<?, ?> riderAI;

    public DragonRiderCombatAI(EntityCitizen owner, ITickRateStateMachine<?> stateMachine, AbstractEntityAIDragonRider<?, ?> parentAI) {
        super(owner, (ITickRateStateMachine) stateMachine);
        this.riderAI = parentAI;

        // Transition-Groups zur Feinderkennung registrieren (Raw-Cast löst Generic-Capture-Inkompatibilität)
        ((ITickRateStateMachine) stateMachine).addTransitionGroup(
                BehaviourStateGroup.GUARD_ABORT_AND_FIGHT,
                new TickingTransition(this::checkForTarget, () -> CombatAIStates.ATTACKING, 5).withName("busy_checkTarget")
        );
        ((ITickRateStateMachine) stateMachine).addTransitionGroup(
                BehaviourStateGroup.GUARD_ABORT_AND_FIGHT,
                new TickingTransition(this::searchNearbyTarget, () -> CombatAIStates.ATTACKING, 80).withName("busy_searchTarget")
        );
    }

    private boolean isDragonPriority() {
        return this.riderAI != null && this.riderAI.assignedDragon == null && this.riderAI.isDragonAvailableInRoost();
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

        EntityCitizen citizen = this.user;
        int maxLevel = citizen.getCitizenData() != null && citizen.getCitizenData().getWorkBuilding() != null
                ? citizen.getCitizenData().getWorkBuilding().getMaxEquipmentLevel()
                : 1;

        int weaponSlot = InventoryUtils.getFirstSlotOfItemHandlerContainingEquipment(
                citizen.getInventoryCitizen(),
                (EquipmentTypeEntry) ModEquipmentTypes.axe.get(),
                0,
                maxLevel
        );

        if (weaponSlot != -1) {
            CitizenItemUtils.setHeldItem(citizen, InteractionHand.MAIN_HAND, weaponSlot);
            return true;
        }
        return false;
    }

    @Override
    protected void doAttack(LivingEntity target) {
        if (this.user.distanceTo(target) > 1.0F) {
            this.moveInAttackPosition(target);
        }

        this.user.swing(InteractionHand.MAIN_HAND);
        this.user.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, (float) SoundUtils.getRandomPitch(this.user.getRandom()));

        double damage = this.getAttackDamage();
        // Direkte DamageSource-Erzeugung über Vanilla-Registry, da DamageSources.source() in Vanilla private ist
        var damageTypeRegistry = target.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        DamageSource source = new DamageSource(damageTypeRegistry.getHolderOrThrow(DamageSourceKeys.GUARD), this.user);
        if (Boolean.TRUE.equals(MineColonies.getConfig().getServer().pvp_mode.get()) && target instanceof Player) {
            source = new DamageSource(damageTypeRegistry.getHolderOrThrow(DamageSourceKeys.GUARD_PVP), this.user);
        }

        // Fire Aspect Verzauberung
        int fireLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FIRE_ASPECT, this.user.getItemInHand(InteractionHand.MAIN_HAND));
        if (fireLevel > 0) {
            target.setSecondsOnFire(fireLevel * 4);
        }

        target.hurt(source, (float) damage);
        target.setLastHurtByMob(this.user);

        if (target instanceof Mob mob && this.user.getCitizenColonyHandler().getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(ResearchConstants.KNIGHT_TAUNT) > 0.0F) {
            mob.setTarget(this.user);
            this.user.getThreatTable().addThreat(this.user, 5);
        }

        this.user.resetFallDistance();
        CitizenItemUtils.damageItemInHand(this.user, InteractionHand.MAIN_HAND, 1);
    }

    @Override
    protected boolean isAttackableTarget(LivingEntity entity) {
        return AbstractEntityAIGuard.isAttackableTarget(this.user, entity);
    }

    @Override
    protected boolean isWithinPersecutionDistance(LivingEntity target) {
        return this.riderAI != null && this.riderAI.isWithinPersecutionDistance(target.blockPosition(), this.getAttackDistance());
    }

    @Override
    protected void onTargetChange(LivingEntity newTarget) {
        super.onTargetChange(newTarget);
        CombatUtils.notifyGuardsOfTarget(this.user, this.target, 1600);
    }

    @Override
    protected boolean skipSearch(LivingEntity entity) {
        if (entity instanceof EntityCitizen citizen && this.user.getRandom().nextInt(10) < 1) {
            if (citizen.getCitizenJobHandler().getColonyJob() instanceof AbstractJobGuard guardJob && guardJob.isAsleep() && this.user.getSensing().hasLineOfSight(citizen)) {
                if (this.riderAI != null) {
                    this.riderAI.setWakeCitizen(citizen);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onTargetDied(LivingEntity entity) {
        if (this.riderAI != null) {
            this.riderAI.incrementActionsDone();
        }
        this.user.getCitizenExperienceHandler().addExperience(15.0D);
        this.user.getCitizenColonyHandler().getColonyOrRegister().getStatisticsManager().increment("mobs_killed", this.user.getCitizenColonyHandler().getColonyOrRegister().getDay());

        ComponentContents contents = entity.getType().getDescription().getContents();
        if (contents instanceof TranslatableContents translatableContents && this.riderAI != null && this.riderAI.building != null) {
            BuildingStatisticsModule stats = this.riderAI.building.getModule(BuildingModules.STATS_MODULE);
            if (stats != null) {
                stats.increment("mobs_killed;" + translatableContents.getKey());
            }
        }

        this.user.decreaseSaturationForContinuousAction();
    }

    @Override
    protected double getAttackDistance() {
        return 2.0D;
    }

    @Override
    protected int getAttackDelay() {
        int reload = 32 - this.user.getCitizenData().getCitizenSkillHandler().getLevel(Skill.Adaptability) / 3;
        return Math.max(reload, 16);
    }

    // Kein @Override, da AttackMoveAI/TargetAI dies intern nicht virtuell deklarieren
    protected int getSearchRange() {
        return 16;
    }

    // Kein @Override, da AttackMoveAI diese Hilfsmethode nicht deklariert, sondern nur MeleeCombatAI
    protected double getCombatMovementSpeed() {
        double levelAdjustment = (double) this.user.getCitizenData().getCitizenSkillHandler().getLevel(Skill.Adaptability) * 0.01D;
        if (this.user.getCitizenData().getWorkBuilding() != null) {
            levelAdjustment += (double) (this.user.getCitizenData().getWorkBuilding().getBuildingLevelEquivalent() - 1) * 0.01D;
        }
        return 1.0D + Math.min(levelAdjustment, 0.3D);
    }

    @Override
    protected PathResult moveInAttackPosition(LivingEntity target) {
        EntityNavigationUtils.walkToPos(this.user, target.blockPosition(), (int) this.getAttackDistance(), false, this.getCombatMovementSpeed());
        return this.user.getNavigation().getPathResult();
    }

    protected double getAttackDamage() {
        double addDmg = 0.0D;
        EntityCitizen citizen = this.user;
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