package net.kb150.dragoncolonies.mixin.client;

import net.magister.bookofdragons.client.render.RiderMatrixDataflow;
import net.magister.bookofdragons.client.render.dragon.DragonRendererAz;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = DragonRendererAz.class, remap = false)
public abstract class DragonRendererAzMultiPassMixin {

    /**
     * Hebelt die tryLockForFrame-Sperre in HeadExtractionLayer aus,
     * damit der Hauptkamera-Pass NIEMALS von Schatten- oder Shader-Pässen blockiert wird.
     */
    @Redirect(
        method = "*",
        at = @At(
            value = "INVOKE",
            target = "Lnet/magister/bookofdragons/client/render/RiderMatrixDataflow;tryLockForFrame(I)Z",
            remap = false
        ),
        remap = false,
        require = 0
    )
    private static boolean dragoncolonies$forceMatrixLock(int entityId) {
        // Zwingt BoD dazu, die Matrix IMMER zu speichern, anstatt den Haupt-Pass zu verwerfen
        return true; 
    }
}