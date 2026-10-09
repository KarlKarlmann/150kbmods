package net.kb150.grubies.client;

import net.kb150.grubies.GrubiesMod;
import net.kb150.grubies.client.sfx.ClientAudioHandler;
import net.kb150.grubies.client.synesthesia.SynesthesiaState;
import net.kb150.grubies.client.vfx.ClientMotionBlur;
import net.kb150.grubies.client.vfx.ClientShaderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientTripHandler {
    public record OutputChannel(String name, Consumer<Float> setter) {}

    public static final List<OutputChannel> CHANNELS = new ArrayList<>();
    public static final int INPUT_COUNT = SynesthesiaState.INPUTS.length;

    private static float[][] weights;
    public static float[] inputGains;
    public static float[] outputGains;
    public static boolean[][] patchConnections;
    public static boolean manualPatchMode = false;
    private static int activeSeed = -1;

    static {
        // Registriert alle Subsystem-Empfänger dynamisch; bestimmt die wirkliche Matrix-Breite
        ClientShaderManager.registerChannels(CHANNELS);
        ClientMotionBlur.registerChannels(CHANNELS);
        ClientAudioHandler.registerChannels(CHANNELS);

        rebuildMatrixDimensions();
    }

    public static void rebuildMatrixDimensions() {
        int outCount = CHANNELS.size();
        weights = new float[INPUT_COUNT][outCount];
        inputGains = new float[INPUT_COUNT];
        outputGains = new float[outCount];
        patchConnections = new boolean[INPUT_COUNT][outCount];
        Arrays.fill(inputGains, 1.0f);
        Arrays.fill(outputGains, 1.0f);
    }

    public static int getOutputCount() { return CHANNELS.size(); }

    public static String getOutputName(int index) {
        return (index >= 0 && index < CHANNELS.size()) ? CHANNELS.get(index).name() : "Out " + index;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Player p = Minecraft.getInstance().player;
        if (p == null) return;

        MobEffectInstance a = p.getEffect(GrubiesMod.TRIP_EFFECT_A.get()), b = p.getEffect(GrubiesMod.TRIP_EFFECT_B.get());
        
        if (manualPatchMode) {
            SynesthesiaState.tick(p, p.tickCount);
            calculateOutputs();
            return;
        }

        if (a != null && b != null) {
            int seed = (a.getAmplifier() << 8) | b.getAmplifier();
            if (seed != activeSeed) {
                activeSeed = seed;
                buildWeights(seed);
                ClientShaderManager.setPipeline("grub_master");
            }
            SynesthesiaState.tick(p, p.tickCount);
            calculateOutputs();
        } else if (activeSeed != -1 && !ClientDebugCommand.forceDebug) {
            resetAll();
        }
    }

    public static void resetAll() {
        activeSeed = -1;
        manualPatchMode = false;
        Arrays.fill(inputGains, 1.0f);
        Arrays.fill(outputGains, 1.0f);
        for (var row : patchConnections) Arrays.fill(row, false);
        
        ClientShaderManager.clearShader();
        ClientAudioHandler.reset();
        ClientMotionBlur.reset();
    }

    private static void buildWeights(int seed) {
        RandomSource r = RandomSource.create(seed);
        int outCount = CHANNELS.size();
        for (int i = 0; i < INPUT_COUNT; i++) {
            for (int j = 0; j < outCount; j++) {
                weights[i][j] = r.nextFloat() > 0.4f ? (r.nextFloat() * 2.0f) - 1.0f : 0.0f;
            }
        }
    }

	private static void calculateOutputs() {
		if (ClientDebugCommand.forceDebug && !manualPatchMode) return;

		int outCount = CHANNELS.size();
		
		// Bereitet den Log nur auf, wenn das Mod-Logging an ist und der Tick stimmt. Spart String-Allokationen im Hotpath.
		Player p = Minecraft.getInstance().player;
		boolean doLog = p != null && p.tickCount % 40 == 0 && GrubiesMod.isLogging("TRIP");
		StringBuilder logB = doLog ? new StringBuilder("Matrix-Outs: ") : null;

		for (int j = 0; j < outCount; j++) {
			float sum = 0.0f;
			for (int i = 0; i < INPUT_COUNT; i++) {
				boolean connected = patchConnections[i][j];
				float w = manualPatchMode 
					? (connected ? (inputGains[i] * outputGains[j]) : 0.0f) 
					: weights[i][j];
				sum += SynesthesiaState.INPUTS[i] * w;
			}
			
			float val = Mth.clamp(sum, -1.0f, 1.0f);
			CHANNELS.get(j).setter().accept(val);
			
			// Filtert Rauschen und inaktive Kanaele (< 1%) rigoros aus dem Log, um den Fokus auf aktive Effekte zu legen
			if (doLog && Math.abs(val) > 0.01f) {
				logB.append(CHANNELS.get(j).name()).append(String.format("=%.2f | ", val));
			}
		}

		if (doLog) {
			String finalLog = logB.toString();
			GrubiesMod.debug("TRIP", finalLog.equals("Matrix-Outs: ") ? "Matrix-Outs: [ALLE STUMM]" : finalLog);
		}

		ClientShaderManager.evaluate();
		ClientMotionBlur.evaluate();
		ClientAudioHandler.evaluate();
	}

    public static boolean setDebugOutput(String paramName, float val) {
        for (OutputChannel ch : CHANNELS) {
            if (ch.name().equalsIgnoreCase(paramName)) {
                ch.setter().accept(val);
                ClientAudioHandler.sync();
                return true;
            }
        }
        return false;
    }

    public static List<String> getDebugParamNames() {
        List<String> list = new ArrayList<>();
        for (OutputChannel ch : CHANNELS) list.add(ch.name());
        return list;
    }

    @SubscribeEvent
    public static void onRenderTick(ViewportEvent.ComputeCameraAngles e) {
        Player p = Minecraft.getInstance().player;
        if (p == null || (activeSeed == -1 && !ClientDebugCommand.forceDebug && !manualPatchMode)) return;

        ClientShaderManager.tickUniforms();
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post e) {
        ClientMotionBlur.render(e.getGuiGraphics());
    }
}