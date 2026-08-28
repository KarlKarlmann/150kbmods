package net.kb150.dragoncolonies.mixin;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.research.util.ResearchConstants;
import com.minecolonies.core.entity.ai.workers.guard.AbstractEntityAIGuard;
import net.kb150.dragoncolonies.ai.AbstractEntityAIDragonRider;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractEntityAIGuard.class, remap = false)
public abstract class AbstractEntityAIGuardMixin {

    @Shadow protected LivingEntity target;
    @Shadow protected int fighttimer;
    @Shadow protected int sleepTimer;

    @Inject(method = "shouldSleep", at = @At("HEAD"), cancellable = true, remap = false)
    private void dragoncolonies$handleShouldSleepOnHead(CallbackInfoReturnable<Boolean> cir) {
        AbstractEntityCitizen worker = ((AbstractAISkeletonAccessor) this).getWorker();

        if (worker != null && worker.getVehicle() instanceof DragonBase) {
            
            if (worker.getLastHurtByMob() == null 
                    && this.target == null 
                    && this.fighttimer <= 0 
                    && !worker.getCitizenData().getCitizenDiseaseHandler().isSick()) {

                double chance = 1.0F / (1.0F + worker.getCitizenColonyHandler().getColonyOrRegister()
                        .getResearchManager().getResearchEffects().getEffectStrength(ResearchConstants.SLEEP_LESS));

                int adaptabilityLevel = (int) (worker.getCitizenData().getCitizenSkillHandler().getLevel(Skill.Adaptability) * 0.5F) + 20;

                if (worker.getRandom().nextInt(adaptabilityLevel) == 1 && worker.getRandom().nextDouble() < chance) {
                    
                    this.sleepTimer = worker.getRandom().nextInt(500) + 2500;
                    
                    if ((Object) this instanceof AbstractEntityAIDragonRider<?, ?> rider) {
                        rider.triggerDragonReturn();
                    }

                    cir.setReturnValue(true); 
                    return;
                }
            }
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "stopSleeping", at = @At("HEAD"), cancellable = true, remap = false)
    private void dragoncolonies$preventStopSleepingOnDragon(CallbackInfo ci) {
        AbstractEntityCitizen worker = ((AbstractAISkeletonAccessor) this).getWorker();

        if (worker != null && worker.getVehicle() instanceof DragonBase) {
            if ((Object) this instanceof AbstractEntityAIDragonRider<?, ?> rider) {
                rider.triggerDragonReturn();
            }
            
            worker.getCitizenExperienceHandler().addExperience(1.0F);
            ci.cancel();
        }
    }
}