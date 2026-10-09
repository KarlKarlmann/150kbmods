package net.kb150.grubies.client.sfx;

import net.kb150.grubies.GrubiesMod;
import net.kb150.grubies.client.ClientTripHandler.OutputChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public class ClientAudioHandler {
    public static float rawPitch = 0.0f, rawMuffle = 0.0f, rawWobble = 0.0f, rawPicker = 0.0f;
    public static float pitch = 1.0f, muffle = 0.0f, wobble = 0.0f, gain = 1.0f;

    private static List<SoundEvent> allSoundsCache = null;
    private static TripPickerSoundInstance activePickerInstance = null;
    private static int activeSeed = Integer.MIN_VALUE;

    public static void registerChannels(List<OutputChannel> list) {
        list.add(new OutputChannel("audioPitch", v -> rawPitch = v));
        list.add(new OutputChannel("audioMuffle", v -> rawMuffle = v));
        list.add(new OutputChannel("audioWobble", v -> rawWobble = v));
        list.add(new OutputChannel("audioPicker", v -> rawPicker = v));
    }

    public static void reset() {
        rawPitch = rawMuffle = rawWobble = rawPicker = muffle = wobble = 0.0f;
        pitch = gain = 1.0f;
        activeSeed = Integer.MIN_VALUE;

        if (activePickerInstance != null) {
            activePickerInstance.stopSound();
            activePickerInstance = null;
        }
        sync();
    }

    public static void evaluate() {
        pitch = 1.0f + rawPitch * 0.5f;
        muffle = Math.abs(rawMuffle);
        wobble = Math.abs(rawWobble);

        updatePickerSound();
        sync();

        // Diagnose-Sonde: Druckt den exakten Audio-Zustand alle 20 Ticks in den Log
        Player p = Minecraft.getInstance().player;
        if (p != null && p.tickCount % 20 == 0 && GrubiesMod.isLogging("SFX")) {
            boolean isPlaying = activePickerInstance != null && Minecraft.getInstance().getSoundManager().isActive(activePickerInstance);
            String soundLoc = activePickerInstance != null ? activePickerInstance.getLocation().toString() : "NULL";
            float vol = activePickerInstance != null ? activePickerInstance.getVolume() : -1.0f;

            GrubiesMod.debug("SFX", "Picker-Diagnose: rawPicker={}|Vol={}|Playing={}|Seed={}|Sound={}", 
                rawPicker, vol, isPlaying, activeSeed, soundLoc);
        }
    }

    private static void updatePickerSound() {
        Player p = Minecraft.getInstance().player;
        if (p == null) return;

        // Einmaliger Registry-Puffer erspart 20Hz-Iteratordruck auf die Forge-Sound-Registry
        if (allSoundsCache == null || allSoundsCache.isEmpty()) {
            allSoundsCache = new ArrayList<>(ForgeRegistries.SOUND_EVENTS.getValues());
        }
        if (allSoundsCache.isEmpty()) return;

        MobEffectInstance a = p.getEffect(GrubiesMod.TRIP_EFFECT_A.get());
        MobEffectInstance b = p.getEffect(GrubiesMod.TRIP_EFFECT_B.get());
        int ampA = a != null ? a.getAmplifier() : 0;
        int ampB = b != null ? b.getAmplifier() : 0;

        // Deterministischer Seed aus UUID und Wurmgift-Amplifiern
        int seed = p.getUUID().hashCode() ^ ((ampA << 16) | (ampB & 0xFFFF));

        if (seed != activeSeed) {
            activeSeed = seed;
            if (activePickerInstance != null) activePickerInstance.stopSound();

            SoundEvent picked = allSoundsCache.get(Math.abs(seed) % allSoundsCache.size());
            activePickerInstance = new TripPickerSoundInstance(picked);
            Minecraft.getInstance().getSoundManager().play(activePickerInstance);

            if (GrubiesMod.isLogging("SFX")) {
                GrubiesMod.debug("SFX", "Gepickter Sound gestartet [Seed {}]: {}", seed, picked.getLocation());
            }
        }
    }

    public static void sync() {
        AudioState.pitch = pitch;
        AudioState.muffle = muffle;
        AudioState.wobble = wobble;
        AudioState.masterGain = gain;
    }
}