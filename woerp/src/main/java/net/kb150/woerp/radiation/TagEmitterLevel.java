package net.kb150.woerp.radiation;

import net.kb150.woerp.Woerp;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public enum TagEmitterLevel {
    LOW("emitters/low", 10.0f, 16.0f),
    MEDIUM("emitters/medium", 25.0f, 24.0f),
    HIGH("emitters/high", 50.0f, 32.0f),
    EXTREME("emitters/extreme", 100.0f, 48.0f);

    public final TagKey<Block> tag;
    public final float emission;
    public final float radius;

    TagEmitterLevel(String path, float emission, float radius) {
        this.tag = TagKey.create(Registries.BLOCK, new ResourceLocation(Woerp.MODID, path));
        this.emission = emission;
        this.radius = radius;
    }

    // Ermittelt blitzschnell, ob ein BlockState einem unserer Strahlungs-Tags entspricht
    public static TagEmitterLevel getForState(BlockState state) {
        for (TagEmitterLevel level : values()) {
            if (state.is(level.tag)) return level;
        }
        return null;
    }
}