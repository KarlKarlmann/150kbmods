package net.kb150.dragoncolonies.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.kb150.dragoncolonies.DragonColonies;
import net.kb150.dragoncolonies.config.DragonColoniesConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Set;

/**
 * Registriert Ingame-Befehle zur Laufzeit-Steuerung der Diagnose-Logging-Kanäle:
 * - /dragoncolonies log status
 * - /dragoncolonies log toggle <channel>
 * - /dragoncolonies log enable <channel>
 * - /dragoncolonies log disable <channel>
 * - /dragoncolonies log clear
 * Alias: /dclog <channel>
 */
@Mod.EventBusSubscriber(modid = DragonColonies.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DragonColoniesCommand {

    private static final List<String> KNOWN_CHANNELS = List.of(
            "DISMOUNT",
            "NAVIGATION",
            "AI",
            "COMBAT",
            "ALL"
    );

    private static final SuggestionProvider<CommandSourceStack> CHANNEL_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(KNOWN_CHANNELS, builder);

    private DragonColoniesCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Hauptbefehl: /dragoncolonies log ...
        var rootNode = Commands.literal("dragoncolonies")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("log")
                        .executes(DragonColoniesCommand::showStatus)
                        .then(Commands.literal("status")
                                .executes(DragonColoniesCommand::showStatus))
                        .then(Commands.literal("clear")
                                .executes(DragonColoniesCommand::clearLogs))
                        .then(Commands.literal("toggle")
                                .then(Commands.argument("channel", StringArgumentType.word())
                                        .suggests(CHANNEL_SUGGESTIONS)
                                        .executes(DragonColoniesCommand::toggleChannel)))
                        .then(Commands.literal("enable")
                                .then(Commands.argument("channel", StringArgumentType.word())
                                        .suggests(CHANNEL_SUGGESTIONS)
                                        .executes(ctx -> setChannel(ctx, true))))
                        .then(Commands.literal("disable")
                                .then(Commands.argument("channel", StringArgumentType.word())
                                        .suggests(CHANNEL_SUGGESTIONS)
                                        .executes(ctx -> setChannel(ctx, false))))
                );

        dispatcher.register(rootNode);

        var aliasNode = Commands.literal("dclog")
                .requires(source -> source.hasPermission(2))
                .executes(DragonColoniesCommand::showStatus)
                .then(Commands.argument("channel", StringArgumentType.word())
                        .suggests(CHANNEL_SUGGESTIONS)
                        .executes(DragonColoniesCommand::toggleChannel));

        dispatcher.register(aliasNode);
    }

    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Set<String> active = DragonColoniesConfig.getActiveChannels();
        boolean logAll = DragonColoniesConfig.isLogAll();

        source.sendSuccess(() -> Component.literal("=== [DragonColonies Logging] ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        
        MutableComponent statusLine = Component.literal("Status: ").withStyle(ChatFormatting.GRAY);
        if (logAll) {
            statusLine.append(Component.literal("ALL (Alle Kanäle aktiv)").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        } else if (active.isEmpty()) {
            statusLine.append(Component.literal("DEAKTIVIERT (Keine Kanäle aktiv)").withStyle(ChatFormatting.RED));
        } else {
            statusLine.append(Component.literal(String.join(", ", active)).withStyle(ChatFormatting.GREEN));
        }
        source.sendSuccess(() -> statusLine, false);

        source.sendSuccess(() -> Component.literal("Verfügbare Kanäle: " + String.join(", ", KNOWN_CHANNELS)).withStyle(ChatFormatting.DARK_GRAY), false);
        source.sendSuccess(() -> Component.literal("Tipp: /dclog <Kanal> zum schnellen Umschalten").withStyle(ChatFormatting.DARK_AQUA), false);
        return 1;
    }

    private static int toggleChannel(CommandContext<CommandSourceStack> ctx) {
        String channel = StringArgumentType.getString(ctx, "channel").toUpperCase();
        boolean enabled = DragonColoniesConfig.toggleChannel(channel);

        MutableComponent message = Component.literal("[DragonColonies] Kanal '")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(channel).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("' ist jetzt: "))
                .append(enabled 
                        ? Component.literal("AKTIV").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                        : Component.literal("DEAKTIVIERT").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));

        ctx.getSource().sendSuccess(() -> message, true);
        return 1;
    }

    private static int setChannel(CommandContext<CommandSourceStack> ctx, boolean enable) {
        String channel = StringArgumentType.getString(ctx, "channel").toUpperCase();
        DragonColoniesConfig.setChannel(channel, enable);

        MutableComponent message = Component.literal("[DragonColonies] Kanal '")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(channel).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("' gesetzt auf: "))
                .append(enable 
                        ? Component.literal("AKTIV").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                        : Component.literal("DEAKTIVIERT").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));

        ctx.getSource().sendSuccess(() -> message, true);
        return 1;
    }

    private static int clearLogs(CommandContext<CommandSourceStack> ctx) {
        DragonColoniesConfig.clearChannels();
        ctx.getSource().sendSuccess(() -> 
                Component.literal("[DragonColonies] Alle Logging-Kanäle wurden deaktiviert.").withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }
}