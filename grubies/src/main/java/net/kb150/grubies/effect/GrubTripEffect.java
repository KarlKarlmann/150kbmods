package net.kb150.grubies.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class GrubTripEffect extends MobEffect {
    public GrubTripEffect() {
        // Tropisch-violette Partikelfarbe, NEUTRAL damit Milch ihn nicht zwingend sofort löscht
        super(MobEffectCategory.NEUTRAL, 0x9932CC);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true; // Ermöglicht serverseitiges Ticking falls nötig
    }
}