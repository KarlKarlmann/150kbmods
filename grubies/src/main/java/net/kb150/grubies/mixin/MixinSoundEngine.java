package net.kb150.grubies.mixin;

import net.kb150.grubies.client.sfx.AudioState;
import net.kb150.grubies.client.synesthesia.SynesthesiaState;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(SoundEngine.class)
public abstract class MixinSoundEngine {
    @Shadow @Final private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;

    // Mojang aktualisiert Pitch/Filter normaler Sounds nur einmalig beim Start.
    // Dieser Hook erzwingt die kontinuierliche LFO-Modulation fuer alle aktiven OpenAL-Kanaele im 20Hz-Engine-Tick.
    @Inject(method = "tickNonPaused", at = @At("TAIL"))
    private void onTickNonPaused(CallbackInfo ci) {
        if (this.instanceToChannel.isEmpty()) return;

        // Nutzt Mojangs interne ChannelHandle-Map zur verlustfreien Thread-Synchronisation
        this.instanceToChannel.forEach((instance, handle) -> handle.execute(channel -> {
            channel.setPitch(instance.getPitch());
        }));
    }
}