package net.kb150.survivorcolonies.entity.ai;

import net.kb150.survivorcolonies.SurvivorColonies;
import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolActions;

import java.util.EnumSet;

/**
 * Adaptives Kampf-Goal fuer Survivor (Nah- und Fernkampf in einem System).
 *
 * Schild-Mechanik ("Deckung und Konter"):
 *  - Der Survivor hebt das Schild, sobald ein Gegner in Schlagnaehe kommt, der ihn angreift,
 *    oder ein Projektil auf ihn zufliegt (kurze Reaktionszeit, damit es menschlich wirkt).
 *  - Schlaegt der Gegner zu, nutzt der Survivor sofort das Zeitfenster fuer einen Konter:
 *    Schild runter, zuschlagen, nach kurzer Pause wieder in Deckung.
 *  - Ist die eigene Waffe bereit und der Gegner in Reichweite, hat Angreifen immer Vorrang.
 *  - Dauerblocken ist begrenzt (Ausdauer), danach folgt eine kurze Pause.
 *  - Mit dem Bogen in der Hand wird nie geblockt.
 */
public class SurvivorCombatGoal extends Goal {
    private final SurvivorEntity survivor;

    // Fernkampf-Parameter
    private int strafeDirection = 1;
    private int strafeTimer = 0;
    private static final double MIN_RANGED_DIST_SQ = 25.0D;  // 5 Bloecke: Flucht/Kiting-Distanz
    private static final double MAX_RANGED_DIST_SQ = 196.0D; // 14 Bloecke: Maximale Feuerreichweite

    // Nahkampf-Parameter
    private int meleeAttackCooldown = 0;
    private int repathDelay = 0;
    private boolean lastTargetSwinging = false;
    private static final int MELEE_COOLDOWN_NO_SHIELD = 18;  // ~0.9 Sekunden zwischen Schlaegen
    private static final int MELEE_COOLDOWN_SHIELD = 24;     // etwas bedachter, wenn ein Schild getragen wird
    private static final int COUNTER_MAX_COOLDOWN = 10;      // Konter nur, wenn der eigene Cooldown fast abgelaufen ist

    // Schild-Parameter
    private boolean guarding = false;
    private int guardTicks = 0;
    private int guardRestTicks = 0;
    private int raiseDelay = 0;
    private int threatTicks = 0;
    private int reactionDelay = 3;
    private boolean incomingProjectile = false;
    private static final int MAX_GUARD_TICKS = 50;           // "Ausdauer": max. 2,5 s am Stueck
    private static final int GUARD_REST_TICKS = 15;          // Pause nach erschoepfter Ausdauer
    private static final int RAISE_DELAY_AFTER_ATTACK = 3;   // Schild bleibt kurz unten nach einem Schlag
    private static final double GUARD_RANGE_EXTRA = 2.5D;    // Deckung beginnt so viele Bloecke vor der Schlagreichweite
    private static final double PROJECTILE_RANGE = 10.0D;

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
        this.meleeAttackCooldown = 0;
        this.repathDelay = 0;
        this.lastTargetSwinging = false;
        this.strafeTimer = 0;

        this.guarding = false;
        this.guardTicks = 0;
        this.guardRestTicks = 0;
        this.raiseDelay = 0;
        this.threatTicks = 0;
        this.incomingProjectile = false;
    }

    @Override
    public void stop() {
        // Zwingend laufende Lade- oder Blockzustaende aufraeumen
        stopGuard();
        if (this.survivor.isUsingItem()) {
            this.survivor.stopUsingItem();
        }
        this.survivor.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = this.survivor.getTarget();
        if (target == null) return;

        double distSq = this.survivor.distanceToSqr(target);
        this.survivor.getLookControl().setLookAt(target, 30.0F, 30.0F);

        ItemStack mainHand = this.survivor.getMainHandItem();
        ItemStack offHand = this.survivor.getOffhandItem();

        if (mainHand.getItem() instanceof BowItem) {
            // Bogen und Schild schliessen sich aus: laufende Deckung beenden
            if (this.guarding) stopGuard();
            tickRangedCombat(target, mainHand, distSq);
            return;
        }

        boolean hasShield = offHand.canPerformAction(ToolActions.SHIELD_BLOCK);
        if (!hasShield && this.guarding) stopGuard();

        tickMeleeCombat(target, distSq, hasShield);
    }

    // ------------------------------------------------------------------------------------
    // NAHKAMPF
    // ------------------------------------------------------------------------------------

    private void tickMeleeCombat(LivingEntity target, double distSq, boolean hasShield) {
        if (this.meleeAttackCooldown > 0) this.meleeAttackCooldown--;
        if (this.repathDelay > 0) this.repathDelay--;

        double reachSq = getAttackReachSqr(target);
        boolean inReach = distSq <= reachSq;
        boolean canSee = this.survivor.getSensing().hasLineOfSight(target);

        // Konter-Fenster: Gegner hat gerade zugeschlagen und ist jetzt verwundbar
        boolean enemySwinging = target.swinging;
        if (enemySwinging && !this.lastTargetSwinging && inReach
                && this.meleeAttackCooldown <= COUNTER_MAX_COOLDOWN) {
            this.meleeAttackCooldown = 0;
        }
        this.lastTargetSwinging = enemySwinging;

        // Bewegung: nur nachlaufen, wenn nicht in Reichweite, und nur selten neu pfaden
        if (!inReach) {
            if (this.repathDelay <= 0 || this.survivor.getNavigation().isDone()) {
                this.repathDelay = 4 + this.survivor.getRandom().nextInt(7);
                double speed = this.guarding ? 1.1D : 1.25D;
                this.survivor.getNavigation().moveTo(target, speed);
            }
        } else {
            this.survivor.getNavigation().stop();
        }

        // Angriff hat Vorrang vor der Deckung
        if (inReach && canSee && this.meleeAttackCooldown <= 0) {
            if (this.guarding) stopGuard();
            this.raiseDelay = hasShield ? RAISE_DELAY_AFTER_ATTACK : 0;
            this.meleeAttackCooldown = hasShield ? MELEE_COOLDOWN_SHIELD : MELEE_COOLDOWN_NO_SHIELD;

            this.survivor.swing(InteractionHand.MAIN_HAND);
            // Vanilla-Angriff: wendet Attribute, Waffen-Modifikatoren und Verzauberungen automatisch an
            this.survivor.doHurtTarget(target);
        }

        if (hasShield) {
            tickShield(target, distSq, reachSq);
        }

        if (this.survivor.tickCount % 10 == 0) {
            SurvivorColonies.debug("COMBAT", "dist=%.2f reach=%.2f cd=%d guard=%s gTicks=%d rest=%d navDone=%s",
                    Math.sqrt(distSq), Math.sqrt(reachSq), this.meleeAttackCooldown,
                    this.guarding, this.guardTicks, this.guardRestTicks,
                    this.survivor.getNavigation().isDone());
        }
    }

    private double getAttackReachSqr(LivingEntity target) {
        // Mittelpunktsabstand: Eigener Radius + Gegner-Radius + etwas Schwertreichweite
        double reach = 1.8D + this.survivor.getBbWidth() / 2.0D + target.getBbWidth() / 2.0D;
        return reach * reach;
    }

    // ------------------------------------------------------------------------------------
    // SCHILD
    // ------------------------------------------------------------------------------------

    private void tickShield(LivingEntity target, double distSq, double reachSq) {
        if (this.guardRestTicks > 0) this.guardRestTicks--;
        if (this.raiseDelay > 0) this.raiseDelay--;

        boolean threat = isThreatened(target, distSq, reachSq);

        if (this.guarding) {
            // Falls die Benutzung von aussen unterbrochen wurde (z. B. Item gewechselt)
            if (!this.survivor.isUsingItem()) {
                this.guarding = false;
                this.guardTicks = 0;
                return;
            }

            this.guardTicks++;
            if (!threat) {
                stopGuard();
            } else if (this.guardTicks >= MAX_GUARD_TICKS) {
                stopGuard();
                this.guardRestTicks = GUARD_REST_TICKS;
            }
            return;
        }

        if (!threat) {
            this.threatTicks = 0;
            return;
        }

        // Kurze Reaktionszeit, damit das Schild nicht "magisch" im selben Tick oben ist
        if (this.threatTicks == 0) {
            this.reactionDelay = 2 + this.survivor.getRandom().nextInt(5);
        }
        this.threatTicks++;

        if (this.threatTicks >= this.reactionDelay
                && this.raiseDelay <= 0
                && this.guardRestTicks <= 0
                && !this.survivor.isUsingItem()) {
            startGuard();
        }
    }

    /**
     * Gilt als Bedrohung: ein Gegner in Schlagnaehe, der den Survivor angreift, oder ein
     * Projektil, das auf ihn zufliegt. Waehrend der Deckung wird der Radius leicht erweitert,
     * damit das Schild an der Grenze nicht flackert.
     */
    private boolean isThreatened(LivingEntity target, double distSq, double reachSq) {
        double range = Math.sqrt(reachSq) + GUARD_RANGE_EXTRA + (this.guarding ? 1.0D : 0.0D);
        boolean near = distSq <= range * range;

        boolean meleeThreat = false;
        if (near) {
            if (target instanceof Mob mob) {
                LivingEntity mobTarget = mob.getTarget();
                meleeThreat = mobTarget == null || mobTarget == this.survivor;
            } else {
                meleeThreat = true;
            }
        }

        return meleeThreat || detectIncomingProjectile();
    }

    private boolean detectIncomingProjectile() {
        // Nur jeden 2. Tick scannen, das reicht voellig
        if (this.survivor.tickCount % 2 != 0) {
            return this.incomingProjectile;
        }

        this.incomingProjectile = false;
        AABB box = this.survivor.getBoundingBox().inflate(PROJECTILE_RANGE, 4.0D, PROJECTILE_RANGE);
        Vec3 eye = this.survivor.getEyePosition();

        for (Projectile projectile : this.survivor.level().getEntitiesOfClass(Projectile.class, box,
                p -> p.isAlive()
                        && !p.onGround()
                        && !(p.getOwner() instanceof SurvivorEntity)
                        && p.getDeltaMovement().lengthSqr() > 0.01D)) {

            Vec3 toMe = eye.subtract(projectile.position());
            double dist = toMe.length();
            if (dist > PROJECTILE_RANGE || dist < 0.5D) continue;

            // Fliegt das Projektil (ungefaehr) in meine Richtung?
            double alignment = projectile.getDeltaMovement().normalize().dot(toMe.normalize());
            if (alignment > 0.85D) {
                this.incomingProjectile = true;
                break;
            }
        }
        return this.incomingProjectile;
    }

    private void startGuard() {
        this.guarding = true;
        this.guardTicks = 0;
        this.survivor.startUsingItem(InteractionHand.OFF_HAND);
    }

    private void stopGuard() {
        this.guarding = false;
        this.guardTicks = 0;
        if (this.survivor.isUsingItem() && this.survivor.getUsedItemHand() == InteractionHand.OFF_HAND) {
            this.survivor.stopUsingItem();
        }
    }

    // ------------------------------------------------------------------------------------
    // FERNKAMPF
    // ------------------------------------------------------------------------------------

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

        // Schuss abgeben mit leichter Streuung
        float inaccuracy = 14.0F - this.survivor.level().getDifficulty().getId() * 4.0F;
        arrow.shoot(dx, dy + horizontalDist * 0.18D, dz, power * 1.6F, Math.max(1.0F, inaccuracy));

        this.survivor.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.survivor.getRandom().nextFloat() * 0.4F + 0.8F));
        this.survivor.level().addFreshEntity(arrow);
    }
}