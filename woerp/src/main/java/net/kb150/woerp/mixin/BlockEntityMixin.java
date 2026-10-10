package net.kb150.woerp.mixin;

import net.kb150.woerp.radiation.ContainerTracker;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin {

    // SRG-Fallbacks (m_6596_) sichern die Injection auf Dedicated Production Servern ab
    @Inject(method = {"setChanged()V", "m_6596_()V"}, at = @At("HEAD"), remap = false)
    private void onSetChanged(CallbackInfo ci) {
        BlockEntity be = (BlockEntity) (Object) this;
        if (be.hasLevel() && !be.getLevel().isClientSide()) {
            ContainerTracker.markDirty(be);
        }
    }
}