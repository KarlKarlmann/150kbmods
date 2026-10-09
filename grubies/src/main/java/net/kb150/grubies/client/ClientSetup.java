package net.kb150.grubies.client;

import net.kb150.grubies.GrubiesMod;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.IEventBus;

public class ClientSetup {
    public static void init(IEventBus modBus) {
        modBus.addListener(ClientSetup::registerItemColors);
    }

    private static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        // Generiert pro Herkunfts-Block eine konsistente, deterministische Farbe für die Raupe
        event.getItemColors().register((stack, tintIndex) -> {
            if (tintIndex != 0) return -1;
            CompoundTag tag = stack.getTag();
            if (tag != null && tag.contains("OriginBlock")) {
                String blockId = tag.getString("OriginBlock");
                return getConsistentColorForBlock(blockId);
            }
            return 0x8B5A2B;
        }, GrubiesMod.GRUB.get());
    }

    private static int getConsistentColorForBlock(String blockId) {
        // HashCode erzeugt eine feste Farbe pro Block-ID, ohne auf Client-Rendering-Contexts angewiesen zu sein
        int hash = blockId.hashCode();
        int r = (hash & 0xFF0000) >> 16;
        int g = (hash & 0x00FF00) >> 8;
        int b = (hash & 0x0000FF);
        
        // Helligkeits-Floor verhindert unsichtbare/schwarze Raupen
        r = Math.max(r, 60);
        g = Math.max(g, 60);
        b = Math.max(b, 60);
        
        return (r << 16) | (g << 8) | b;
    }
}