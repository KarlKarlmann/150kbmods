package net.kb150.grubies.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.kb150.grubies.GrubiesMod;
import net.kb150.grubies.config.GrubiesConfig;
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

@Mod.EventBusSubscriber(modid = GrubiesMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GrubiesCommand {

    private static final List<String> KNOWN_CHANNELS = List.of("LOOT", "EAT", "TRIP", "ALL");

    private static final SuggestionProvider<CommandSourceStack> CHANNEL_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(KNOWN_CHANNELS, builder);

    private GrubiesCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var rootNode = Commands.literal("grubies")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("log")
                        .executes(GrubiesCommand::showStatus)
                        .then(Commands.literal("status").executes(GrubiesCommand::showStatus))
                        .then(Commands.literal("clear").executes(GrubiesCommand::clearLogs))
                        .then(Commands.literal("toggle")
                                .then(Commands.argument("channel", StringArgumentType.word())
                                        .suggests(CHANNEL_SUGGESTIONS)
                                        .executes(GrubiesCommand::toggleChannel)))
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

        // Schnellzugriff Alias
        var aliasNode = Commands.literal("grublog")
                .requires(source -> source.hasPermission(2))
                .executes(GrubiesCommand::showStatus)
                .then(Commands.argument("channel", StringArgumentType.word())
                        .suggests(CHANNEL_SUGGESTIONS)
                        .executes(GrubiesCommand::toggleChannel));

        dispatcher.register(aliasNode);
    }

    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Set<String> active = GrubiesConfig.getActiveChannels();

        source.sendSuccess(() -> Component.literal("=== [Grubies Logging] ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        
        MutableComponent statusLine = Component.literal("Status: ").withStyle(ChatFormatting.GRAY);
        if (GrubiesConfig.isLogAll()) {
            statusLine.append(Component.literal("ALL (Alle Kanäle aktiv)").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        } else if (active.isEmpty()) {
            statusLine.append(Component.literal("DEAKTIVIERT").withStyle(ChatFormatting.RED));
        } else {
            statusLine.append(Component.literal(String.join(", ", active)).withStyle(ChatFormatting.GREEN));
        }
        source.sendSuccess(() -> statusLine, false);
        return 1;
    }

    private static int toggleChannel(CommandContext<CommandSourceStack> ctx) {
        String channel = StringArgumentType.getString(ctx, "channel").toUpperCase();
        boolean enabled = GrubiesConfig.toggleChannel(channel);

        MutableComponent message = Component.literal("[Grubies] Kanal '")
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
        GrubiesConfig.setChannel(channel, enable);

        MutableComponent message = Component.literal("[Grubies] Kanal '")
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
        GrubiesConfig.clearChannels();
        ctx.getSource().sendSuccess(() -> 
                Component.literal("[Grubies] Alle Logging-Kanäle wurden deaktiviert.").withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }
}