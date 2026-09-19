
package net.kb150.survivorcolonies.entity.ai;

import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Baut am frühen Abend (Activity.EVENING_SETUP) ein Wollzelt auf und lässt es danach
 * DAUERHAFT stehen - es dient tagsüber wie nachts als Fluchtpunkt (siehe SurvivorFleeToTentGoal).
 * Ob der Survivor abends reingeht und schläft (Activity.SLEEPING) oder das Zelt nur als
 * stehendes Versteck nutzt und lieber jagt/scavengt (Activity.NIGHT_PATROL), entscheidet
 * SurvivorEntity#updateActivity() – dieses Goal fragt das nur noch ab.
 *
 * Das Zelt ist bewusst nur 1 Block hoch (Dach direkt über dem Gang) - normale Monster
 * (2 Blöcke hoch) passen dadurch gar nicht erst rein. Der Survivor selbst muss dafür
 * die Kriech-Pose (SWIMMING) einnehmen.
 *
 * Wird das Goal durch ein höher priorisiertes Kampf-/Flucht-Goal unterbrochen, wird NICHTS
 * abgerissen. Der Zustand bleibt erhalten (State.STANDBY) und der Survivor macht nahtlos
 * weiter, sobald der Kampf vorbei ist.
 */
public class SurvivorTentGoal extends Goal {
    private final SurvivorEntity survivor;
    private final List<BlockPos> blocksToBuild = new ArrayList<>();

    private BlockPos tentInside = null;
    private State state = State.IDLE;
    private int actionTimer = 0;

    // Mindestabstand zum bekannten Lagerfeuer, damit das Zelt nicht ins Feuer gebaut wird
    private static final double MIN_DISTANCE_FROM_CAMPFIRE = 3.5D;

    private static final Set<Item> WOOL_ITEMS = Set.of(
        Items.WHITE_WOOL, Items.ORANGE_WOOL, Items.MAGENTA_WOOL, Items.LIGHT_BLUE_WOOL,
        Items.YELLOW_WOOL, Items.LIME_WOOL, Items.PINK_WOOL, Items.GRAY_WOOL,
        Items.LIGHT_GRAY_WOOL, Items.CYAN_WOOL, Items.PURPLE_WOOL, Items.BLUE_WOOL,
        Items.BROWN_WOOL, Items.GREEN_WOOL, Items.RED_WOOL, Items.BLACK_WOOL
    );

    private enum State {
        IDLE,        // kein Zelt vorhanden
        BUILDING,    // wird gerade aufgebaut
        STANDBY,     // fertig aufgebaut, Survivor macht gerade etwas anderes (z.B. Tag über/Nachtpatrouille)
        ENTERING,    // läuft rein, um zu schlafen
        SLEEPING     // liegt drin und schläft
    }

    public SurvivorTentGoal(SurvivorEntity survivor) {
        this.survivor = survivor;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (survivor.isPassenger()) return false;

        SurvivorActivity act = survivor.getActivity();
        return switch (this.state) {
            // Kein Zelt vorhanden -> nur im frühen Abend neu aufbauen
            case IDLE -> act == SurvivorActivity.EVENING_SETUP;
            // Aktion, die schon läuft, bis zum Ende durchziehen (auch nach kurzer Kampf-Unterbrechung)
            case BUILDING, ENTERING -> true;
            // Zelt steht bereits (Tag oder Nachtpatrouille): nur reingehen, wenn er jetzt schlafen will
            case STANDBY -> act == SurvivorActivity.SLEEPING;
            // Weiterschlafen, solange die Activity das hergibt
            case SLEEPING -> act == SurvivorActivity.SLEEPING;
        };
    }

    @Override
    public void start() {
        if (this.state == State.IDLE) {
            if (!planTentLocation()) {
                // Kein gültiger Bauplatz in der Nähe (Hügel/Wand im Weg, oder zu nah am Lagerfeuer) ->
                // state bleibt IDLE, canUse() wird nächsten Tick erneut versucht (evtl. hat er sich
                // inzwischen woandershin bewegt).
                return;
            }
            this.state = State.BUILDING;
            this.actionTimer = 0;
            return;
        }

        if (this.state == State.STANDBY) {
            // Zelt steht schon -> er will jetzt schlafen gehen
            this.state = State.ENTERING;
            this.actionTimer = 0;
            return;
        }

        // BUILDING / ENTERING: wurde nur kurz unterbrochen (z.B. Kampf) -> einfach fortsetzen,
        // NICHT neu initialisieren, sonst gingen die bereits gesetzten Blöcke "vergessen".
    }

    /**
     * Sucht einen gültigen Bauplatz: probiert die aktuelle Blickrichtung und die anderen
     * 3 Himmelsrichtungen durch. Ein Platz gilt nur als gültig, wenn
     *  - alle Wand-/Dach-Positionen frei sind (kein Hügel, keine Wand im Weg),
     *  - der Gang selbst frei ist UND auf festem Boden steht (kein halb im Hang stehendes Zelt),
     *  - genug Abstand zum bekannten Lagerfeuer besteht (baut ihm sonst quasi ins Feuer).
     * Bei Erfolg werden tentInside und blocksToBuild befüllt.
     */
    private boolean planTentLocation() {
        BlockPos knownCampfire = survivor.getKnownCampfirePos();
        Direction preferredDir = survivor.getDirection();
        Direction[] tryOrder = {
            preferredDir,
            preferredDir.getClockWise(),
            preferredDir.getOpposite(),
            preferredDir.getCounterClockWise()
        };

        for (Direction dir : tryOrder) {
            BlockPos entrance = survivor.blockPosition().relative(dir);
            Direction left = dir.getCounterClockWise();
            Direction right = dir.getClockWise();

            List<BlockPos> walkway = new ArrayList<>();
            List<BlockPos> wallsAndRoof = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                BlockPos forwardPos = entrance.relative(dir, i);
                walkway.add(forwardPos);
                wallsAndRoof.add(forwardPos.relative(left));
                wallsAndRoof.add(forwardPos.relative(right));
                wallsAndRoof.add(forwardPos.above());
            }
            wallsAndRoof.add(entrance.relative(dir, 2)); // Rückwand, verschließt den Gang

            if (isLocationValid(wallsAndRoof, walkway, knownCampfire)) {
                this.tentInside = entrance.relative(dir);
                this.blocksToBuild.clear();
                this.blocksToBuild.addAll(wallsAndRoof);
                return true;
            }
        }
        return false;
    }

    private boolean isLocationValid(List<BlockPos> wallsAndRoof, List<BlockPos> walkway, BlockPos knownCampfire) {
        for (BlockPos pos : wallsAndRoof) {
            if (!survivor.level().getBlockState(pos).canBeReplaced()) return false;
            if (tooCloseToCampfire(pos, knownCampfire)) return false;
        }
        for (BlockPos pos : walkway) {
            // Der Gang selbst muss frei sein...
            if (!survivor.level().getBlockState(pos).canBeReplaced()) return false;
            // ...und auf festem Boden stehen (verhindert "halb im Hügel"-Zelte oder Zelte über Abgründen)
            BlockPos floor = pos.below();
            if (!survivor.level().getBlockState(floor).isFaceSturdy(survivor.level(), floor, Direction.UP)) {
                return false;
            }
            if (tooCloseToCampfire(pos, knownCampfire)) return false;
        }
        return true;
    }

    private boolean tooCloseToCampfire(BlockPos pos, BlockPos knownCampfire) {
        return knownCampfire != null
                && pos.distSqr(knownCampfire) < (MIN_DISTANCE_FROM_CAMPFIRE * MIN_DISTANCE_FROM_CAMPFIRE);
    }

    @Override
    public void tick() {
        switch (state) {
            case BUILDING -> {
                actionTimer++;
                if (actionTimer >= 6) { // Alle 0,3 Sekunden einen Block setzen
                    actionTimer = 0;
                    if (!blocksToBuild.isEmpty()) {
                        BlockPos targetPos = blocksToBuild.remove(0);
                        // Platz nochmal prüfen (kann sich seit dem Planen minimal geändert haben)
                        if (survivor.level().getBlockState(targetPos).canBeReplaced()) {
                            // Wenn er Wolle im Inventar hat, wird sie verbraucht - hat er keine,
                            // baut er trotzdem (kostenlos), siehe tryConsumeOneWool().
                            tryConsumeOneWool();
                            Block woolBlock = getWoolColorForUUID(survivor.getUUID());
                            survivor.level().setBlockAndUpdate(targetPos, woolBlock.defaultBlockState());

                            survivor.swing(InteractionHand.MAIN_HAND);
                            survivor.level().playSound(null, targetPos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                            survivor.getLookControl().setLookAt(targetPos.getX() + 0.5D, targetPos.getY() + 0.5D, targetPos.getZ() + 0.5D);
                        }
                        // Falls die Stelle doch nicht frei ist: einfach überspringen (kein Abbruch nötig,
                        // die Fläche wurde beim Planen bereits geprüft, das hier ist nur ein Sicherheitsnetz).
                    } else {
                        // Fertig aufgebaut: sofort schlafen legen, oder erstmal nur stehen lassen
                        // (z.B. weil er nachts noch jagen/scavengen will, oder es noch Tag ist)
                        state = (survivor.getActivity() == SurvivorActivity.SLEEPING)
                                ? State.ENTERING
                                : State.STANDBY;
                        actionTimer = 0;
                    }
                }
            }
            case STANDBY -> {
                // Zelt steht dauerhaft, hier passiert nichts aktiv - der GoalSelector lässt dieses
                // Goal ohnehin nur laufen, solange canContinueToUse() true liefert (siehe unten).
                // Der eigentliche Übergang nach ENTERING passiert über start(), sobald canUse()
                // erneut true wird (er will schlafen). Tagsüber/beim Patrouillieren bleibt es einfach
                // stehen und dient SurvivorFleeToTentGoal als Fluchtpunkt.
            }
            case ENTERING -> {
                // Kriech-Pose (Swimming) erzwingen
                survivor.setPose(Pose.SWIMMING);
                survivor.getNavigation().moveTo(tentInside.getX() + 0.5D, tentInside.getY(), tentInside.getZ() + 0.5D, 0.6D);

                if (survivor.distanceToSqr(tentInside.getX() + 0.5D, tentInside.getY(), tentInside.getZ() + 0.5D) < 0.8D) {
                    survivor.getNavigation().stop();
                    state = State.SLEEPING;
                }
            }
            case SLEEPING -> {
                survivor.setPose(Pose.SWIMMING);
                survivor.getNavigation().stop();
                // Kein automatischer Abbau mehr: er steht morgens einfach auf und lässt das
                // Zelt stehen (Übergang zurück nach STANDBY passiert automatisch über
                // canContinueToUse() -> stop(), sobald die Activity nicht mehr SLEEPING ist).
            }
            case IDLE -> { /* nichts zu tun */ }
        }
    }

    @Override
    public boolean canContinueToUse() {
        return switch (state) {
            case BUILDING, ENTERING -> true;
            case SLEEPING -> survivor.getActivity() == SurvivorActivity.SLEEPING;
            // STANDBY & IDLE: dieses Goal ist "inaktiv" -> gibt MOVE/LOOK/JUMP für
            // Scavenge, Eat & Co. frei, blockiert also nicht unnötig den Tag/die Nachtpatrouille.
            default -> false;
        };
    }

    @Override
    public void stop() {
        survivor.setPose(Pose.STANDING);
        survivor.getNavigation().stop();

        if (state == State.ENTERING || state == State.SLEEPING) {
            // Aufgewacht, oder unterbrochen (z.B. Kampf-/Flucht-Goal hat MOVE übernommen) ->
            // Zelt bleibt stehen, Survivor "pausiert" im Freien und macht woanders weiter.
            state = State.STANDBY;
        }
        // BUILDING (unvollständig) und STANDBY selbst: Zustand bleibt exakt erhalten,
        // beim nächsten start() geht es nahtlos weiter.
    }

    /**
     * Position im Zeltinneren, solange ein Zelt existiert (auch im STANDBY-Zustand, also
     * auch tagsüber). Wird u.a. von SurvivorFleeToTentGoal als Fluchtziel genutzt und von
     * SurvivorScavengeGoal, um gierigen ("money") Survivorn auch im Schlaf noch das
     * Aufsammeln von Items in Zelt-Nähe zu erlauben.
     */
    public BlockPos getTentAnchorPos() {
        return (this.state == State.IDLE) ? null : this.tentInside;
    }

    /** Nur für Debug-Logging: aktueller interner Zustand als String. */
    public String getDebugState() {
        return this.state.name();
    }

    /** Entfernt 1 Wolle (egal welche Farbe) aus dem Inventar, falls vorhanden. Gibt true zurück, wenn erfolgreich. */
    private boolean tryConsumeOneWool() {
        var inv = survivor.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && WOOL_ITEMS.contains(stack.getItem())) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    private Block getWoolColorForUUID(UUID uuid) {
        Block[] wools = {
            Blocks.WHITE_WOOL, Blocks.ORANGE_WOOL, Blocks.MAGENTA_WOOL, Blocks.LIGHT_BLUE_WOOL,
            Blocks.YELLOW_WOOL, Blocks.LIME_WOOL, Blocks.PINK_WOOL, Blocks.GRAY_WOOL,
            Blocks.LIGHT_GRAY_WOOL, Blocks.CYAN_WOOL, Blocks.PURPLE_WOOL, Blocks.BLUE_WOOL,
            Blocks.BROWN_WOOL, Blocks.GREEN_WOOL, Blocks.RED_WOOL, Blocks.BLACK_WOOL
        };
        int index = Math.abs(uuid.hashCode()) % wools.length;
        return wools[index];
    }
}
