package net.kb150.woerp.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class WoerpMutationEffect extends MobEffect {
    public WoerpMutationEffect(int color) {
        // NEUTRAL verhindert, dass Mobs panisch Milch suchen oder Vanilla-KIs fliehen, erlaubt aber native Attribut-Modifikatoren
        super(MobEffectCategory.NEUTRAL, color);
    }
}