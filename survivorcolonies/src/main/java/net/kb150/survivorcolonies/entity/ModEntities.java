package net.kb150.survivorcolonies.entity;

import net.kb150.survivorcolonies.SurvivorColonies;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES = 
        DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, SurvivorColonies.MODID);

    public static final RegistryObject<EntityType<SurvivorEntity>> SURVIVOR = 
        ENTITIES.register("survivor", () -> EntityType.Builder.of(SurvivorEntity::new, MobCategory.CREATURE)
                .sized(0.6F, 1.95F)
                .clientTrackingRange(8)
                .build("survivor"));

    public static void register(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }
}