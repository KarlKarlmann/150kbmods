package net.kb150.grubies.client;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.kb150.grubies.client.vfx.ClientShaderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientDebugCommand {
    public static boolean forceDebug = false;

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("grubtest")
                .then(Commands.literal("off").executes(ctx -> {
                    forceDebug = false;
                    ClientTripHandler.resetAll();
                    msg("§c[VFX/SFX] Debug-Modus beendet & alle Subsysteme genullt.");
                    return 1;
                }))
                .then(Commands.literal("load")
                    .then(Commands.argument("pipeline", StringArgumentType.word())
                        .executes(ctx -> {
                            forceDebug = true;
                            String pipe = StringArgumentType.getString(ctx, "pipeline");
                            ClientShaderManager.setPipeline(pipe);
                            msg("§a[VFX] Pipeline geladen: " + pipe);
                            return 1;
                        })))
                .then(Commands.literal("set")
                    .then(Commands.argument("param", StringArgumentType.word())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ClientTripHandler.getDebugParamNames(), builder))
                    .then(Commands.argument("val", FloatArgumentType.floatArg())
                        .executes(ctx -> {
                            forceDebug = true;
                            String p = StringArgumentType.getString(ctx, "param");
                            float v = FloatArgumentType.getFloat(ctx, "val");

                            if (ClientTripHandler.setDebugOutput(p, v)) {
                                msg("§a[VFX/SFX] Output " + p + " = " + v);
                            } else {
                                msg("§c[VFX/SFX] Unbekannter Output-Parameter: " + p);
                            }
                            return 1;
                        }))))
				.then(Commands.literal("gui").executes(ctx -> {
					Minecraft.getInstance().execute(() -> 
						Minecraft.getInstance().setScreen(new net.kb150.grubies.client.gui.SynesthesiaMixerScreen())
					);
					msg("§a[VFX/SFX] Synesthesia Patch-Pult geöffnet.");
					return 1;
						}))	
        );
    }

    private static void msg(String txt) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal(txt));
    }
}