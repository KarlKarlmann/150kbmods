package net.kb150.reward_box.event;

import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.kb150.reward_box.RewardBox;
import net.kb150.reward_box.network.PacketSyncBoxData;
import net.kb150.reward_box.network.RewardBoxNetwork;
import net.kb150.reward_box.util.RewardBoxConfigManager;

import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = RewardBox.MODID)
public class RewardBoxSyncEvent {

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        Map<String, String[]> textureMap = new HashMap<>();
        
        for (String boxId : RewardBoxConfigManager.getLoadedBoxIds()) {
            net.minecraft.resources.ResourceLocation tex = RewardBoxConfigManager.getTextureForBox(boxId);
            net.minecraft.resources.ResourceLocation glow = RewardBoxConfigManager.getGlowTextureForBox(boxId);
            
            if (tex != null) {
                textureMap.put(boxId, new String[]{
                    tex.toString(), 
                    glow != null ? glow.toString() : ""
                });
            }
        }

        PacketSyncBoxData packet = new PacketSyncBoxData(textureMap);

        if (event.getPlayer() != null) {
            RewardBoxNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(event::getPlayer), packet);
        } else {
            RewardBoxNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
        }
    }
}