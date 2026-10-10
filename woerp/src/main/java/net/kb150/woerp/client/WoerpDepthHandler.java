package net.kb150.woerp.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import net.kb150.woerp.Woerp;
import net.kb150.woerp.mixin.PostChainAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

@Mod.EventBusSubscriber(modid = Woerp.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WoerpDepthHandler {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;

        Minecraft mc = Minecraft.getInstance();
        PostChain chain = mc.gameRenderer.currentEffect();
        if (chain == null) return;

        RenderTarget dst = ((PostChainAccessor) chain).getCustomRenderTargets().get("worlddepth");
        if (dst == null || dst.getDepthTextureId() == -1) return;

        RenderTarget src = mc.getMainRenderTarget();

        // Vanilla leert den Z-Buffer vor dem Ego-Hand-Pass. Der Blit friert die echte Block-Tiefe der Welt vor dem Clear in 'worlddepth' ein.
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, src.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, dst.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, src.width, src.height, 0, 0, dst.width, dst.height, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);

        src.bindWrite(false);
    }
}