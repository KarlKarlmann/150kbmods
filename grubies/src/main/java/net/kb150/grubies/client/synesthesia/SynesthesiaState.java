package net.kb150.grubies.client.synesthesia;

import net.kb150.grubies.GrubiesMod;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

public class SynesthesiaState {
    public static final float[] INPUTS = new float[13];
    
    public static final String[] INPUT_NAMES = {
        "Camera Velocity",     // 0
        "Movement Velocity",   // 1
        "HP Mangel",           // 2
        "Hunger Mangel",       // 3
        "Audio Pulse (Smooth)",// 4: Geglätteter Envelope Follower (ideal für Wobblyness/Pulsieren)
        "Audio Peak (Instant)",// 5: Unkomprimierter Roh-Impuls (ideal für Strobe/Glitches)
        "LFO Kombi",           // 6
        "LFO Square",          // 7
        "LFO Herz",            // 8
        "LFO Chill",           // 9
        "Trip Duration",       // 10
        "Light Level",          // 11
        "Magnetic Field"           // 12
    };

    public static float lastYRot = 0.0f, lastXRot = 0.0f;
    private static float frameAudioEnergy = 0.0f, rawAudioPeak = 0.0f;
    private static int maxTripDuration = 0;

    // MixinChannel isoliert 3D-Welt-Audio (relative=false); blendet Kopf-Sounds zur Rückkopplungsvermeidung aus
    public static void addAudioEnergy(float energy) { 
        frameAudioEnergy += energy; 
        if (energy > rawAudioPeak) rawAudioPeak = energy;
    }

    public static float lfo(int waveType, float t) {
        return switch (waveType) {
            case 1 -> (float) ((Math.sin(t) + Math.sin(t * 2.0f) * 0.5f) * 0.4 + 0.5);
            case 2 -> Math.signum((float) Math.sin(t * 4.0f)) > 0 ? 1.0f : 0.0f;
            case 3 -> {
                float pulse = (float) Math.sin(t);
                yield pulse > 0.6f ? (float) Math.pow(pulse, 4.0) : 0.0f;
            }
            default -> (float) (Math.sin(t * 0.5f) * 0.5 + 0.5);
        };
    }

    public static void tick(Player p, float ticks) {
        float dY = Mth.wrapDegrees(p.getYRot() - lastYRot), dX = Mth.wrapDegrees(p.getXRot() - lastXRot);
        lastYRot = p.getYRot(); lastXRot = p.getXRot();

        // 0 & 1: Bewegung
        INPUTS[0] = Mth.clamp((float) Math.sqrt(dY * dY + dX * dX) / 20.0f, 0.0f, 1.0f);
        INPUTS[1] = Mth.clamp((float) p.getDeltaMovement().lengthSqr() / 0.2f, 0.0f, 1.0f);
        
        // 2 & 3: Zustand
        INPUTS[2] = 1.0f - Mth.clamp(p.getHealth() / p.getMaxHealth(), 0.0f, 1.0f);
        INPUTS[3] = 1.0f - Mth.clamp(p.getFoodData().getFoodLevel() / 20.0f, 0.0f, 1.0f);

        // 4: Audio Pulse (Geglättete Hüllkurve)
        if (frameAudioEnergy > 0.0f) {
            INPUTS[4] = Mth.clamp(INPUTS[4] + frameAudioEnergy * 0.25f, 0.0f, 1.0f);
            frameAudioEnergy = 0.0f;
        }
        INPUTS[4] = INPUTS[4] > 0.01f ? INPUTS[4] * 0.88f : 0.0f;

        // 5: Audio Peak (Direkter Spike für harte Impulse)
        INPUTS[5] = Mth.clamp(rawAudioPeak, 0.0f, 1.0f);
        rawAudioPeak = 0.0f;

        // 6 bis 9: LFOs
        INPUTS[6] = lfo(1, ticks * 0.05f);
        INPUTS[7] = lfo(2, ticks * 0.10f);
        INPUTS[8] = lfo(3, ticks * 0.08f);
        INPUTS[9] = lfo(0, ticks * 0.03f);

        // 10: Trip-Dauer
        MobEffectInstance eA = p.getEffect(GrubiesMod.TRIP_EFFECT_A.get()), eB = p.getEffect(GrubiesMod.TRIP_EFFECT_B.get());
        int curDur = Math.max(eA != null ? eA.getDuration() : 0, eB != null ? eB.getDuration() : 0);
        if (curDur > maxTripDuration) maxTripDuration = curDur;
        if (curDur <= 0) maxTripDuration = 0;
        INPUTS[10] = maxTripDuration > 0 ? (float) curDur / maxTripDuration : 0.0f;

        // 11 & 12: Umwelt
        INPUTS[11] = p.level().getMaxLocalRawBrightness(p.blockPosition()) / 15.0f;
        INPUTS[12] = (Mth.wrapDegrees(p.getYRot()) + 180.0f) / 360.0f;
    }
}