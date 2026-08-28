package net.kb150.survivorcolonies.client.model;

import net.kb150.survivorcolonies.SurvivorColonies;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;

public class SurvivorModelLayers {
    public static final ModelLayerLocation SURVIVOR_STEVE = new ModelLayerLocation(
        new ResourceLocation(SurvivorColonies.MODID, "survivor"), "steve");

    public static final ModelLayerLocation SURVIVOR_ALEX = new ModelLayerLocation(
        new ResourceLocation(SurvivorColonies.MODID, "survivor"), "alex");
}