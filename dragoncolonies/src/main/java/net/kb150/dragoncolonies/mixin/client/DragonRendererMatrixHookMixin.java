package net.kb150.dragoncolonies.mixin.client;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.magister.bookofdragons.client.render.dragon.DragonRendererAz;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = DragonRendererAz.class, remap = false)
public abstract class DragonRendererMatrixHookMixin {

    @Redirect(
        method = "*",
        at = @At(
            value = "INVOKE",
            target = "Lnet/magister/bookofdragons/entity/base/dragon/DragonBase;getFirstPassenger()Lnet/minecraft/world/entity/Entity;",
            remap = false
        ),
        remap = false,
        require = 0
    )
    private Entity dragoncolonies$fakePlayerForMatrixExtraction(DragonBase dragon) {
        Entity passenger = dragon.getFirstPassenger();
        if (passenger instanceof AbstractEntityCitizen && Minecraft.getInstance().player != null) {
            return Minecraft.getInstance().player;
        }
        return passenger;
    }

    @Redirect(
        method = "*",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getFirstPassenger()Lnet/minecraft/world/entity/Entity;",
            remap = false
        ),
        remap = false,
        require = 0
    )
    private Entity dragoncolonies$fakePlayerForMatrixExtractionEntity(Entity entity) {
        if (entity instanceof DragonBase dragon && dragon.getFirstPassenger() instanceof AbstractEntityCitizen && Minecraft.getInstance().player != null) {
            return Minecraft.getInstance().player;
        }
        return entity.getFirstPassenger();
    }
}