package net.kb150.grubies.client.sfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public class TripPickerSoundInstance extends AbstractTickableSoundInstance {
    public TripPickerSoundInstance(SoundEvent sound) {
        super(sound, SoundSource.AMBIENT, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        // Mindestens 0.001f; 0.0f laesst Mojangs SoundEngine den Sound beim play()-Call sofort verwerfen
        this.volume = Math.max(0.0f, Math.abs(ClientAudioHandler.rawPicker));
        this.relative = true;
    }

    @Override
    public void tick() {
        // 0.001f als Untergrenze haelt den OpenAL-Channel in Minecrafts SoundEngine aktiv
        this.volume = Math.max(0.0f, Math.abs(ClientAudioHandler.rawPicker));
        float ticks = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.tickCount : 0f;
        this.pitch = AudioState.calcPitch(ticks);
    }

    public void stopSound() {
        this.stop();
    }
}