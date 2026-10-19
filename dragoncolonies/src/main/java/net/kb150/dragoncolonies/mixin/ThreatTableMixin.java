package net.kb150.dragoncolonies.mixin;

import com.minecolonies.api.entity.ai.combat.threat.IThreatTableEntity;
import com.minecolonies.api.entity.ai.combat.threat.ThreatTable;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.kb150.dragoncolonies.DragonColonies;
import net.kb150.dragoncolonies.util.ColonyRelationHelper;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ThreatTable.class, remap = false)
public abstract class ThreatTableMixin<T extends LivingEntity & IThreatTableEntity> {

    @Shadow private T owner;

    // Verhindert, dass kollateraler Schaden durch Kolonie-Drachen einen Vergeltungsbefehl bei Bürgern triggert
    @Inject(method = "addThreat", at = @At("HEAD"), cancellable = true)
    private void dragoncolonies$blockColonyDragonThreat(LivingEntity attacker, int additionalThreat, CallbackInfo ci) {
        if (this.owner instanceof AbstractEntityCitizen citizen && attacker instanceof DragonBase dragon) {
            if (ColonyRelationHelper.isSameColony(citizen, dragon)) {
                DragonColonies.debug("COMBAT", "[THREAT-BLOCKED] Bedrohung von Drache {} gegen Kolonist {} unterdrueckt (gleiche Kolonie)",
                        dragon.getName().getString(), citizen.getName().getString());
                ci.cancel();
            }
        }
    }
}