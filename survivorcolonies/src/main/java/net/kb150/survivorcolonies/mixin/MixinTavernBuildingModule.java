package net.kb150.survivorcolonies.mixin;

import com.minecolonies.core.colony.buildings.modules.TavernBuildingModule;
import net.kb150.survivorcolonies.SurvivorColonies;
import net.kb150.survivorcolonies.config.SurvivorConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TavernBuildingModule.class, remap = false)
public class MixinTavernBuildingModule {
    
    // Prüft vor dem Ausführen der MineColonies-Spawn-Logik, ob diese überhaupt erlaubt ist.
    @Inject(method = "spawnVisitorInternal", at = @At("HEAD"), cancellable = true)
    private void survivorcolonies_preventTavernSpawns(CallbackInfo ci) {
        if (!SurvivorConfig.ALLOW_TAVERN_VISITORS.get()) {
            SurvivorColonies.LOGGER.debug("[SurvivorColonies] Tavernen-Spawn durch Config blockiert.");
            ci.cancel();
        }
    }
}