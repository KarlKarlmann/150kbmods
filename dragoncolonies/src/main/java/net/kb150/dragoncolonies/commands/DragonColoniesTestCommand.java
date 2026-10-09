package net.kb150.dragoncolonies.command;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.kb150.dragoncolonies.DragonColonies;
import net.kb150.dragoncolonies.ai.AbstractEntityAIDragonRider;
import net.kb150.dragoncolonies.ai.DragonNavigationHandler;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DragonColonies.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DragonColoniesTestCommand {

    private DragonColoniesTestCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dctest")
            .requires(s -> s.hasPermission(2))
            .then(Commands.literal("slope_landing").executes(DragonColoniesTestCommand::testSlopeLanding))
            .then(Commands.literal("orbit").executes(DragonColoniesTestCommand::testOrbit))
            .then(Commands.literal("repath_spam").executes(DragonColoniesTestCommand::testRepathSpam))
            .then(Commands.literal("mount_phase").executes(DragonColoniesTestCommand::testMountPhase))
        );
    }

    // Ermittelt den naheliegendsten Reiter im Radius, um Tests ohne feste Entity-IDs zu wiederholen.
    private static AbstractEntityAIDragonRider<?, ?> findNearestRider(ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(20.0D);
        for (AbstractEntityCitizen citizen : player.serverLevel().getEntitiesOfClass(AbstractEntityCitizen.class, box)) {
            AbstractEntityAIDragonRider<?, ?> rider = AbstractEntityAIDragonRider.getRiderForCitizen(citizen);
            if (rider != null) return rider;
        }
        return null;
    }

    // Test 1: Simuliert Anflug auf Klippe/Hang, um Mid-Air-Dismount & Fallschaden gezielt zu fangen.
    private static int testSlopeLanding(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            AbstractEntityAIDragonRider<?, ?> rider = findNearestRider(p);
            if (rider == null) return msg(p, "§cKeine Drachenwache im Umkreis von 20 Blöcken!");

            BlockPos target = p.blockPosition().below(6).relative(p.getDirection(), 8);
            msg(p, "§e[DCTEST] Anflug auf Hang-Ziel: " + target.toShortString());
            DragonNavigationHandler.handleDragonFlight(rider.getCitizen(), target, 1);
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    // Test 2: Setzt die Wache in den Orbit-Loiter über dem Spieler, um Kippen/Flippen zu untersuchen.
    private static int testOrbit(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            AbstractEntityAIDragonRider<?, ?> rider = findNearestRider(p);
            if (rider == null) return msg(p, "§cKeine Drachenwache im Umkreis von 20 Blöcken!");

            BlockPos orbitPos = p.blockPosition().above(15);
            msg(p, "§e[DCTEST] Orbit-Flug um: " + orbitPos.toShortString());
            DragonNavigationHandler.handleDragonFlight(rider.getCitizen(), orbitPos, 1);
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    // Test 3: Befeuert den Handler mit raschen Wegpunktwechseln zur Prüfung von BoD-Kollisionen.
    private static int testRepathSpam(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            AbstractEntityAIDragonRider<?, ?> rider = findNearestRider(p);
            if (rider == null) return msg(p, "§cKeine Drachenwache im Umkreis von 20 Blöcken!");

            BlockPos pos = p.blockPosition();
            msg(p, "§e[DCTEST] Starte Repath-Spam (20 Wegpunkte)...");
            for (int i = 0; i < 20; i++) {
                BlockPos target = pos.offset((i % 4) * 3, 0, (i / 4) * 3);
                DragonNavigationHandler.handleDragonFlight(rider.getCitizen(), target, 1);
            }
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    // Test 4: Löst manuell die Mount-Phase aus, um Trennung/Gummiband-Effekt nachvollziehbar zu machen.
    private static int testMountPhase(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            AbstractEntityAIDragonRider<?, ?> rider = findNearestRider(p);
            if (rider == null) return msg(p, "§cKeine Drachenwache im Umkreis von 20 Blöcken!");

            AbstractEntityCitizen citizen = rider.getCitizen();
            DragonBase dragon = rider.getAssignedDragon();

            if (dragon != null) {
                if (citizen.isPassenger()) citizen.stopRiding();
                msg(p, "§e[DCTEST] Starte erzwungene Mount-Phase...");
                rider.handleMountingPhase(citizen);
            }
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int msg(ServerPlayer p, String text) {
        p.sendSystemMessage(Component.literal(text));
        return 0;
    }
}