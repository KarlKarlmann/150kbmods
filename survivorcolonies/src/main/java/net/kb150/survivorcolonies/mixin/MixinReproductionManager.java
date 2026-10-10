package net.kb150.survivorcolonies.mixin;

import com.minecolonies.core.colony.managers.ReproductionManager;
import net.kb150.survivorcolonies.SurvivorColonies;
import net.kb150.survivorcolonies.config.SurvivorConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

@Mixin(value = ReproductionManager.class, remap = false)
public class MixinReproductionManager {

    @Shadow private Random random;

    // Prueft vor allen Haus- und Elternberechnungen, ob die Chance 1:X erfolgreich ist
    @Inject(method = "trySpawnChild", at = @At("HEAD"), cancellable = true)
    private void survivorcolonies_applyBirthChance(CallbackInfo ci) {
        int denominator = SurvivorConfig.CHILD_SPAWN_CHANCE_DENOMINATOR.get();

        // 0 = Geburten vollständig deaktiviert
        if (denominator <= 0) {
            SurvivorColonies.LOGGER.debug("[SurvivorColonies-Debug] Geburten sind per Config deaktiviert.");
            ci.cancel();
            return;
        }

        // Bei Werten > 1 wird gewürfelt (1 Chance aus X)
        if (denominator > 1) {
            boolean success = this.random.nextInt(denominator) == 0;
            SurvivorColonies.LOGGER.debug("[SurvivorColonies-Debug] Geburten-Wurf 1:{}: {}", denominator, success ? "ERFOLG" : "FEHLGESCHLAGEN");
            if (!success) {
                ci.cancel();
            }
        }
    }
}