package net.kb150.woerp.radiation;

import net.kb150.woerp.Woerp;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public enum ItemRadiationLevel {
    LOW("items/low", 1.0f),
    MEDIUM("items/medium", 3.0f),
    HIGH("items/high", 8.0f),
    EXTREME("items/extreme", 20.0f);

    public final TagKey<Item> tag;
    public final float emissionPerItem;

    ItemRadiationLevel(String path, float emissionPerItem) {
        // Tag-Kopplung entkoppelt Mod-Items von Java-Code: Datapacks weisen Fremd-Items wie Uran oder radioaktive Erden ohne Recompile Strahlungsstufen zu
        this.tag = TagKey.create(Registries.ITEM, new ResourceLocation(Woerp.MODID, path));
        this.emissionPerItem = emissionPerItem;
    }
}