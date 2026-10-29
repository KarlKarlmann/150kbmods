package net.kb150.dragoncolonies.mixin;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.kb150.dragoncolonies.util.ColonyRelationHelper;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.liberanimalium.fauna.api.AnimalHandle;
import net.magister.liberanimalium.fauna.api.Relation;
import net.magister.liberanimalium.fauna.api.RelationType;
import net.magister.liberanimalium.fauna.identity.Temper;
import net.magister.liberanimalium.fauna.know.Knowledge;
import net.magister.liberanimalium.fauna.know.Representation;
import net.magister.liberanimalium.fauna.know.Wants;
import net.magister.liberanimalium.fauna.sense.Perception;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.UUID;

/**
 * Verankert die Kolonie-Zugehoerigkeit und Reiter-Bindung direkt in FAUNAs Gehirn (Knowledge).
 * Bringt der KI bei, dass Passagiere und Kolonie-Siedler Rudelmitglieder (RelationType.PACK)
 * sind und unterdrueckt dadurch Jagd-, Angriffs- und Furcht-Triebe (HuntDrive, EnmityDrive).
 */
@Mixin(value = Knowledge.class, remap = false)
public abstract class KnowledgeMixin {

    @Shadow public abstract Collection<Representation> all();
    @Shadow abstract void bend(Representation rep, Relation bent);

    @Inject(
        method = "integrate(Lnet/magister/liberanimalium/fauna/api/AnimalHandle;Lnet/magister/liberanimalium/fauna/sense/Perception;JLnet/magister/liberanimalium/fauna/identity/Temper;ZLnet/magister/liberanimalium/fauna/know/Wants;)V",
        at = @At("TAIL"),
        remap = false
    )
    private void dragoncolonies$pacifyColonyInMind(
        AnimalHandle handle,
        Perception perception,
        long now,
        Temper temper,
        boolean cornered,
        Wants wants,
        CallbackInfo ci
    ) {
        if (handle.entity() instanceof DragonBase dragon) {
            int colonyId = ColonyRelationHelper.getDragonColonyId(dragon);
            boolean hasPassengers = !dragon.getPassengers().isEmpty();
            if (colonyId <= 0 && !hasPassengers) return;

            if (!(dragon.level() instanceof ServerLevel serverLevel)) return;

            Knowledge self = (Knowledge) (Object) this;

            for (Representation rep : this.all()) {
                UUID targetId = rep.id();

                boolean isFriend = false;
                for (Entity p : dragon.getPassengers()) {
                    if (p.getUUID().equals(targetId)) {
                        isFriend = true;
                        break;
                    }
                }

                if (!isFriend) {
                    Entity targetEntity = serverLevel.getEntity(targetId);
                    if (targetEntity instanceof AbstractEntityCitizen citizen) {
                        if (ColonyRelationHelper.isSameColony(citizen, dragon)) {
                            isFriend = true;
                        }
                    } else if (targetEntity instanceof DragonBase otherDragon) {
                        if (ColonyRelationHelper.isSameColony(dragon, otherDragon)) {
                            isFriend = true;
                        }
                    } else if (targetEntity instanceof Player player) {
                        if (ColonyRelationHelper.isPlayerInColony(player, dragon)) {
                            isFriend = true;
                        }
                    }
                }

                if (isFriend) {
                    if (rep.relation().type() != RelationType.PACK) {
                        this.bend(rep, new Relation(RelationType.PACK, 1.0F));
                    }
                    self.social().stage(targetId, 1.0F);
                }
            }
        }
    }
}