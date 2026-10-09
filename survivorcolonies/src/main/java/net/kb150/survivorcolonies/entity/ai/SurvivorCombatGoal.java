package net.kb150.survivorcolonies.entity.ai;

import net.kb150.survivorcolonies.SurvivorColonies;
import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Adaptives Kampf-Goal fuer Survivor (Nah- und Fernkampf in einem System).
 * Schaltet dynamisch zwischen Schwert/Nahkampf und Bogen/Kiting um.
 */
public class SurvivorCombatGoal extends Goal {
    private final SurvivorEntity survivor;
    
    // Fernkampf-Parameter
    private int bowChargeTicks = 0;
    private int strafeDirection = 1;
    private int strafeTimer = 0;
    private static final double MIN_RANGED_DIST_SQ = 25.0D;  // 5 Bloecke: Flucht/Kiting-Distanz
    private static final double MAX_RANGED_DIST_SQ = 196.0D; // 14 Bloecke: Maximale Feuerreichweite

    // Nahkampf-Parameter
    private int meleeAttackCooldown = 0;
    private static final int BASE_MELEE_COOLDOWN = 18; // ~0.9 Sekunden zwischen Schlaegen

    // Schild-Parameter
    private boolean isBlocking = false;
    private int blockCooldown = 0;
    private int currentBlockDuration = 0;
    private static final int MAX_BLOCK_TICKS = 60; // Max. 3 Sekunden Dauerblocken gegen Stalls

    public SurvivorCombatGoal(SurvivorEntity survivor) {
        this.survivor = survivor;
        // Blockiert Bewegung und Blickrichtung fuer volle Orientierung auf den Gegner
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.survivor.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }

        // Bei akuter Flucht-Prioritaet uebernimmt SurvivorFleeToTentGoal
        return !this.survivor.wantsToFleeFromTarget();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.survivor.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        return !this.survivor.wantsToFleeFromTarget();
    }

    @Override
    public void start() {
        this.bowChargeTicks = 0;
        this.meleeAttackCooldown = 0;
        this.strafeTimer = 0;
        this.isBlocking = false;
        this.blockCooldown = 0;
        this.currentBlockDuration = 0;
    }

    @Override
    public void stop() {
        // Zwingend laufende Lade- oder Blockzustaende aufraeumen
        if (this.survivor.isUsingItem()) {
            this.survivor.stopUsingItem();
        }
        this.isBlocking = false;
        this.survivor.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = this.survivor.getTarget();
        if (target == null) return;

        double distSq = this.survivor.distanceToSqr(target);
        this.survivor.getLookControl().setLookAt(target, 30.0F, 30.0F);

        ItemStack mainHand = this.survivor.getMainHandItem();
        ItemStack offHand = this.survivor.getOffhandItem();

        // 1. Taktisches Schild-Handling (nur wenn nicht gerade der Bogen gespannt wird)
        handleShieldDefense(target, offHand, distSq, mainHand.getItem() instanceof BowItem);

        if (this.isBlocking) {
            return;
        }

        // 2. Zweig nach Waffentyp
        if (mainHand.getItem() instanceof BowItem) {
            tickRangedCombat(target, mainHand, distSq);
        } else {
            tickMeleeCombat(target, distSq);
        }
    }

    private void handleShieldDefense(LivingEntity target, ItemStack offHand, double distSq, boolean isHoldingBow) {
        if (!(offHand.getItem() instanceof ShieldItem)) {
            if (this.isBlocking) stopBlocking();
            return;
        }

        if (this.blockCooldown > 0) {
            this.blockCooldown--;
        }

        if (this.isBlocking) {
            this.currentBlockDuration++;
            // Block nach Maximaldauer oder wenn Gegner zu weit weg ist loesen
            if (this.currentBlockDuration >= MAX_BLOCK_TICKS || distSq > 64.0D) {
                stopBlocking();
                this.blockCooldown = 25;
            }
            return;
        }

        // Bogen-Schuetzen blocken nicht, um ihren Ladezyklus nicht zu zerstoeren
        if (isHoldingBow) return;

        // Block-Entscheidung: Nahbereich (< 4.5 Bloecke) und Feind holt zum Schlag aus
        if (this.blockCooldown <= 0 && distSq <= 20.0D) {
            boolean enemyThreat = target.swinging || target.isUsingItem() || (this.survivor.hurtTime > 0);
            if (enemyThreat && this.survivor.getRandom().nextFloat() < 0.65F) {
                startBlocking();
            }
        }
    }

    private void startBlocking() {
        this.isBlocking = true;
        this.currentBlockDuration = 0;
        this.survivor.startUsingItem(InteractionHand.OFF_HAND);
    }

    private void stopBlocking() {
        this.isBlocking = false;
        this.currentBlockDuration = 0;
        if (this.survivor.isUsingItem()) {
            this.survivor.stopUsingItem();
        }
    }

    private void tickRangedCombat(LivingEntity target, ItemStack bowStack, double distSq) {
        // Taktische Navigation & Kiting
        if (distSq < MIN_RANGED_DIST_SQ) {
            // Gegner zu nah: Rueckwaerts vom Gegner wegkiten
            Vec3 fleeDir = this.survivor.position().subtract(target.position()).normalize();
            Vec3 fleePos = this.survivor.position().add(fleeDir.scale(4.0D));
            this.survivor.getNavigation().moveTo(fleePos.x, fleePos.y, fleePos.z, 1.25D);
        } else if (distSq > MAX_RANGED_DIST_SQ) {
            // Gegner zu weit weg: In Feuerdistanz vorruecken
            this.survivor.getNavigation().moveTo(target, 1.1D);
        } else {
            // Im optimalen Fenster: Leichtes Strafing, um schwerer getroffen zu werden
            this.strafeTimer++;
            if (this.strafeTimer >= 30 + this.survivor.getRandom().nextInt(20)) {
                this.strafeTimer = 0;
                this.strafeDirection = this.survivor.getRandom().nextBoolean() ? 1 : -1;
            }

            Vec3 toTarget = target.position().subtract(this.survivor.position()).normalize();
            Vec3 strafeVec = new Vec3(-toTarget.z * this.strafeDirection, 0, toTarget.x * this.strafeDirection);
            Vec3 strafeDest = this.survivor.position().add(strafeVec.scale(2.5D));
            this.survivor.getNavigation().moveTo(strafeDest.x, strafeDest.y, strafeDest.z, 0.9D);
        }

        // Bogen spannen und abschiessen
        if (this.survivor.isUsingItem()) {
            int ticksUsing = this.survivor.getTicksUsingItem();
            float power = BowItem.getPowerForTime(ticksUsing);

            // Sichtlinie zum Ziel pruefen
            boolean hasLineOfSight = this.survivor.getSensing().hasLineOfSight(target);

            // Nach mindestens 20 Ticks (volle Spannkraft) abfeuern
            if (power >= 0.8F && hasLineOfSight) {
                shootArrow(target, bowStack, power);
                this.survivor.stopUsingItem();
            } else if (!hasLineOfSight && ticksUsing > 60) {
                // Abbrechen wenn das Ziel hinter eine Wand gelaufen ist
                this.survivor.stopUsingItem();
            }
        } else {
            // Bogen neu anspannen, wenn Sichtlinie besteht
            if (this.survivor.getSensing().hasLineOfSight(target)) {
                this.survivor.startUsingItem(InteractionHand.MAIN_HAND);
            }
        }
    }

    private void shootArrow(LivingEntity target, ItemStack bowStack, float power) {
        if (this.survivor.level().isClientSide) return;

        Arrow arrow = new Arrow(this.survivor.level(), this.survivor);
        
        // Verhindert, dass Spieler unendliche Pfeile durch Beschuss farmen koennen
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;

        // Ballistische Flugbahnberechnung
        double dx = target.getX() - this.survivor.getX();
        double dy = target.getY(0.3333333333333333D) - arrow.getY();
        double dz = target.getZ() - this.survivor.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        // Verstaerkung des Bogens durch Enchants
        int powerLvl = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, bowStack);
        if (powerLvl > 0) {
            arrow.setBaseDamage(arrow.getBaseDamage() + (double) powerLvl * 0.5D + 0.5D);
        }

        int punchLvl = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, bowStack);
        if (punchLvl > 0) {
            arrow.setKnockback(punchLvl);
        }

        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, bowStack) > 0) {
            arrow.setSecondsOnFire(100);
        }

        // Schuess abgeben mit leichter Streuung
        float inaccuracy = 14.0F - this.survivor.level().getDifficulty().getId() * 4.0F;
        arrow.shoot(dx, dy + horizontalDist * 0.18D, dz, power * 1.6F, Math.max(1.0F, inaccuracy));

        this.survivor.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.survivor.getRandom().nextFloat() * 0.4F + 0.8F));
        this.survivor.level().addFreshEntity(arrow);
    }

    private void tickMeleeCombat(LivingEntity target, double distSq) {
        if (this.meleeAttackCooldown > 0) {
            this.meleeAttackCooldown--;
        }

        // Ansturm auf den Gegner
        this.survivor.getNavigation().moveTo(target, 1.25D);

        // Schlagreichweite pruefen (Vanilla Angriffsdistanz inklusive Entity-Bounding-Box)
        double reachSq = getAttackReachSqr(target);
        if (distSq <= reachSq && this.meleeAttackCooldown <= 0) {
            this.meleeAttackCooldown = BASE_MELEE_COOLDOWN;
            this.survivor.swing(InteractionHand.MAIN_HAND);
            
            // Standard Vanilla mob attack wendet Attribute, Waffen-Modifikatoren und Verzauberungen vollautomatisch an
            this.survivor.doHurtTarget(target);
        }
    }

    private double getAttackReachSqr(LivingEntity target) {
        return (double) (this.survivor.getBbWidth() * 2.0F * this.survivor.getBbWidth() * 2.0F + target.getBbWidth());
    }
}