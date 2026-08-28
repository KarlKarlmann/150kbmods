package net.kb150.meteorshower;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MeteorShower.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MeteorCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("meteor")
                .requires(source -> source.hasPermission(2)) // Nur Ops (Cheats an)
                
                // NEU: Logik für den Hauptbefehl, damit er im Chat erkannt wird
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal("§eNutze /meteor strike oder /meteor resetEvent"), false);
                    return 1;
                })
                
                // Befehl 1: /meteor strike
                .then(Commands.literal("strike")
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            ServerLevel level = source.getLevel();
                            BlockPos targetPos = BlockPos.containing(source.getPosition()); // Wo der Spieler steht
                            
                            // Spawnt den Meteor 100 Blöcke weiter weg (damit er dir nicht auf den Kopf fällt)
                            BlockPos offsetPos = targetPos.offset(100, 0, 100);
                            
                            MeteorServerManager.forceSpawnSingleMeteor(level, offsetPos);
                            
                            source.sendSuccess(() -> Component.literal("§cWARNUNG: Test-Meteor im Anflug!"), true);
                            return 1;
                        })
                )
                
                // Befehl 2: /meteor resetEvent
                .then(Commands.literal("resetEvent")
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            ServerLevel level = source.getLevel();
                            
                            MeteorServerManager.resetAndStartEvent(level);
                            
                            source.sendSuccess(() -> Component.literal("§aEvent wurde zurückgesetzt. Schauer beginnt!"), true);
                            return 1;
                        })
                )
        );
    }
}