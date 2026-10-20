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
                float maxItemEmission = 0.0f;
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack stack = handler.getStackInSlot(i);
                    float slotEmission = ItemRadiationRegistry.getStackEmission(stack);
                    if (slotEmission > maxItemEmission) maxItemEmission = slotEmission;
                }

                maxItemEmission = Math.min(500.0f, maxItemEmission);
                EmitterSavedData data = EmitterSavedData.get(level);
                long currentTick = level.getServer().getTickCount();
                RadiationEmitter existing = data.getEmitterAt(be.getBlockPos());

                if (maxItemEmission > 0) {
                    if (existing == null) {
                        data.addEmitter(new RadiationEmitter(be.getBlockPos(), maxItemEmission, currentTick));
                        Woerp.LOGGER.info("[WOERP-CONTAINER-DEBUG] Neuer Emitter bei {} mit Basis-Emission {}", be.getBlockPos(), maxItemEmission);
                    } else {
                        // Dynamisches Update des existierenden Emitters statt Ignorieren weiterer Items
                        existing.setBaseEmission(maxItemEmission);
                        data.setDirty();
                    }
                } else if (existing != null && existing.isActive()) {
                    existing.endTick = currentTick;
                    data.setDirty();
                    Woerp.LOGGER.info("[WOERP-CONTAINER-DEBUG] Kiste bei {} geleert. Zerfall aktiv.", be.getBlockPos());
                }
            });
        }
        DIRTY_QUEUE.clear();
    }
}