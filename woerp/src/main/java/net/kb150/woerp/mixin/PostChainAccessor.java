package net.kb150.woerp.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

@Mixin(PostChain.class)
public interface PostChainAccessor {
    @Accessor("passes")
    List<PostPass> getPasses();

    // Vermeidet Reflection-Overhead beim Auslesen des 'worlddepth'-Targets im 60Hz-Render-Loop
    @Accessor("customRenderTargets")
    Map<String, RenderTarget> getCustomRenderTargets();
}