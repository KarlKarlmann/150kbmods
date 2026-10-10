package net.kb150.woerp.radiation;

import net.kb150.woerp.Woerp;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import java.util.HashSet;
import java.util.Set;

public class ContainerTracker {
    private static final Set<BlockEntity> DIRTY_QUEUE = new HashSet<>();

    public static void markDirty(BlockEntity be) {
        DIRTY_QUEUE.add(be);
    }

    public static void processDirtyContainers() {
        if (DIRTY_QUEUE.isEmpty()) return;

        for (BlockEntity be : DIRTY_QUEUE) {
            if (be.isRemoved() || !(be.getLevel() instanceof ServerLevel level)) continue;

            be.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                float emission = 0.0f;
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack stack = handler.getStackInSlot(i);
                    emission += ItemRadiationRegistry.getStackEmission(stack);
                }

                EmitterSavedData data = EmitterSavedData.get(level);
                long currentTick = level.getServer().getTickCount();
                RadiationEmitter existing = data.getEmitterAt(be.getBlockPos());

                if (emission > 0) {
                    if (existing == null || !existing.isActive()) {
                        data.addEmitter(new RadiationEmitter(be.getBlockPos(), emission, 24.0f, currentTick));
                        Woerp.LOGGER.debug("[WOERP-DEBUG] Mixin-Catch: BE bei {} hat Strahlung {}. Emitter registriert.", be.getBlockPos(), emission);
                    }
                } else if (existing != null && existing.isActive()) {
                    existing.endTick = currentTick;
                    data.setDirty();
                    Woerp.LOGGER.debug("[WOERP-DEBUG] Mixin-Catch: BE bei {} geleert. Emitter auf Zerfall gesetzt.", be.getBlockPos());
                }
            });
        }
        DIRTY_QUEUE.clear();
    }
}