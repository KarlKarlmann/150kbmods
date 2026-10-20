package net.kb150.woerp.radiation;

import net.kb150.woerp.Woerp;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public enum TagEmitterLevel {
    LOW("emitters/low", 125.0f),
    MEDIUM("emitters/medium", 250.0f),
    HIGH("emitters/high", 375.0f),
    EXTREME("emitters/extreme", 500.0f);

    public final TagKey<Block> tag;
    public final float emission;

    TagEmitterLevel(String path, float emission) {
        this.tag = TagKey.create(Registries.BLOCK, new ResourceLocation(Woerp.MODID, path));
        this.emission = emission;
    }

    public static TagEmitterLevel getForState(BlockState state) {
        for (TagEmitterLevel level : values()) {
            if (state.is(level.tag)) return level;
        }
        return null;
    }
}