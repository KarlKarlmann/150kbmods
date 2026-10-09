package net.kb150.grubies.mixin;

import com.mojang.blaze3d.audio.Channel;
import net.kb150.grubies.client.sfx.AudioState;
import net.kb150.grubies.client.sfx.OpenALAudioEngine;
import net.kb150.grubies.client.synesthesia.SynesthesiaState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Channel.class)
public abstract class MixinChannel {
    @Shadow @Final private int source;

    // TAIL garantiert, dass this.source von OpenAL eine valide ID zugewiesen bekommen hat
    @Inject(method = "play", at = @At("TAIL"))
    private void onPlay(CallbackInfo ci) {
        OpenALAudioEngine.applyDirectFilter(this.source, AudioState.muffle);
    }

    @ModifyVariable(method = "setPitch", at = @At("HEAD"), argsOnly = true)
    private float modifyPitch(float originalPitch) {
        float ticks = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.tickCount : 0f;
        return originalPitch * AudioState.calcPitch(ticks);
    }

    @Inject(method = "setPitch", at = @At("TAIL"))
    private void onSetPitch(float f, CallbackInfo ci) {
        OpenALAudioEngine.applyDirectFilter(this.source, AudioState.muffle);
    }

    @Inject(method = "setSelfPosition", at = @At("HEAD"))
    private void onSetSelfPosition(Vec3 vec3, CallbackInfo ci) {
        Player p = Minecraft.getInstance().player;
        if (p == null || this.source <= 0) return;

        double dX = vec3.x - p.getX(), dY = vec3.y - p.getY(), dZ = vec3.z - p.getZ();
        float distSqr = (float) (dX * dX + dY * dY + dZ * dZ);
        
        float gain = AL10.alGetSourcef(this.source, AL10.AL_GAIN);
        float effVol = gain / (1.0f + (float) Math.sqrt(distSqr) * 0.1f);

        if (effVol > 0.03f) {
            SynesthesiaState.addAudioEnergy(effVol);
        }
    }
}