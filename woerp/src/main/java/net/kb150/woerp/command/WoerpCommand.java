package net.kb150.woerp.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.kb150.woerp.capability.ChunkRadiationProvider;
import net.kb150.woerp.capability.PlayerRadiationProvider;
import net.kb150.woerp.network.SyncRadiationPacket;
import net.kb150.woerp.network.WoerpNetwork;
import net.kb150.woerp.radiation.EmitterSavedData;
import net.kb150.woerp.radiation.RadiationEmitter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WoerpCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("woerp")
            .requires(src -> src.hasPermission(2))
            .then(Commands.literal("inspect")
                .executes(ctx -> inspectEnvironment(ctx.getSource())))
            .then(Commands.literal("setdose")
                .then(Commands.argument("value", FloatArgumentType.floatArg(0.0f, 500.0f))
                    .executes(ctx -> setTestDose(ctx.getSource(), FloatArgumentType.getFloat(ctx, "value")))))
        );
    }

    private static int inspectEnvironment(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        ServerPlayer player = src.getPlayer();
        if (player == null) return 0;

        EmitterSavedData data = EmitterSavedData.get(level);
        long tick = level.getServer().getTickCount();

        src.sendSuccess(() -> Component.literal("§e[WOERP INSPECT] Server Tick: " + tick), false);
        src.sendSuccess(() -> Component.literal("§eAktive Emitter in Dim: " + data.getEmitters().size()), false);

        for (RadiationEmitter e : data.getEmitters()) {
            double dist = Math.sqrt(e.pos.distSqr(player.blockPosition()));
            src.sendSuccess(() -> Component.literal(String.format(" §7- Pos: %s | Base: %.1f | Radius: %.1f | Active: %s | Dist: %.1fm",
                    e.pos.toShortString(), e.baseEmission, RadiationEmitter.GLOBAL_RADIUS, e.isActive(), dist)), false);
        }

        level.getChunkAt(player.blockPosition()).getCapability(ChunkRadiationProvider.RADIATION_CAP).ifPresent(cap ->
            src.sendSuccess(() -> Component.literal(String.format("§aPlayer Chunk Ambient: %.2f | LastUpdate: %d", cap.getAmbient(), cap.getLastUpdateTick())), false)
        );

        player.getCapability(PlayerRadiationProvider.PLAYER_RADIATION_CAP).ifPresent(cap ->
            src.sendSuccess(() -> Component.literal(String.format("§bPlayer Body Dose: %.2f", cap.getBodyDose())), false)
        );

        return 1;
    }

    private static int setTestDose(CommandSourceStack src, float val) {
        ServerPlayer player = src.getPlayer();
        if (player == null) return 0;

        // Persistiert den Wert in der Entity-Capability, damit der 20Hz-Loop ihn nicht ueberschreibt
        player.getCapability(PlayerRadiationProvider.PLAYER_RADIATION_CAP).ifPresent(cap -> {
            cap.setBodyDose(val);

            ServerLevel level = player.serverLevel();
            LevelChunk pChunk = level.getChunkSource().getChunkNow(player.blockPosition().getX() >> 4, player.blockPosition().getZ() >> 4);
            float ambient = (pChunk != null) ? pChunk.getCapability(ChunkRadiationProvider.RADIATION_CAP).map(c -> c.getAmbient()).orElse(0.0f) : 0.0f;

            WoerpNetwork.sendToPlayer(new SyncRadiationPacket(ambient, val, List.of(player.blockPosition())), player);
            src.sendSuccess(() -> Component.literal(String.format("§aKoerperdosis gesetzt: %.1f (Ambient: %.1f)", val, ambient)), false);
        });

        return 1;
    }
}