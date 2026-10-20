package net.kb150.woerp.client.layer;

import net.kb150.woerp.Woerp;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;

public class WoerpModelLayers {
    private static ModelLayerLocation loc(String id) {
        return new ModelLayerLocation(new ResourceLocation(Woerp.MODID, id), "main");
    }

    public static final ModelLayerLocation HORN = loc("mutation_horn");
    public static final ModelLayerLocation EYESTALK = loc("mutation_eyestalk");
    public static final ModelLayerLocation SECOND_FACE = loc("mutation_second_face");
    public static final ModelLayerLocation PUSTULES = loc("mutation_pustules");
    public static final ModelLayerLocation CREST = loc("mutation_crest");
    public static final ModelLayerLocation SPUR = loc("mutation_spur");
    public static final ModelLayerLocation CARAPACE = loc("mutation_carapace");
    public static final ModelLayerLocation CLUBFOOT = loc("mutation_clubfoot");
    public static final ModelLayerLocation MAW = loc("mutation_maw");
}