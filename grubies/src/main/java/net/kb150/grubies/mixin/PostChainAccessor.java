package net.kb150.grubies.mixin;

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

    // Direkter FBO-Map-Zugriff vermeidet Mappings-Brüche zwischen Forge/NeoForge/Vanilla
    @Accessor("customRenderTargets")
    Map<String, RenderTarget> getCustomRenderTargets();
}