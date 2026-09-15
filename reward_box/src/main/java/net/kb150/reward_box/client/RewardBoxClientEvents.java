package net.kb150.reward_box.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.kb150.reward_box.RewardBox;
import net.kb150.reward_box.client.screen.RewardBoxAnimationScreen;

@Mod.EventBusSubscriber(modid = RewardBox.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class RewardBoxClientEvents {

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        // Wir ziehen die Kamera durch Manipulation des Field of Views in die Kiste hinein.
        if (Minecraft.getInstance().screen instanceof RewardBoxAnimationScreen screen && screen.playAnimation) {
            float elapsed = screen.getElapsedSeconds();
            
            // Reiner Kamera-Zoom (kein Gewackel der Kamera selbst)
            if (elapsed < 2.0f) {
                float intensity = elapsed / 2.0f; 
                event.setFOV(event.getFOV() - (35.0f * (intensity * intensity)));
            } 
            else if (elapsed < 2.5f) {
                float snapIntensity = 1.0f - ((elapsed - 2.0f) * 2.0f); 
                if (snapIntensity > 0) {
                    event.setFOV(event.getFOV() - (35.0f * snapIntensity));
                }
            }
        }
    }
}