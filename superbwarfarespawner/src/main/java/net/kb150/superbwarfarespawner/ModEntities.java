package net.kb150.superbwarfarespawner;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = "superbwarfarespawner", bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "superbwarfarespawner");

    // Wir registrieren jetzt 3 separate Marker, die mit den 3 Config-Profilen matchen
    public static final RegistryObject<EntityType<VehicleMarkerEntity>> MARKER_A = ENTITIES.register("marker_a",
            () -> EntityType.Builder.of(VehicleMarkerEntity::new, MobCategory.CREATURE)
                    .sized(4.0F, 3.0F).build("marker_a"));

    public static final RegistryObject<EntityType<VehicleMarkerEntity>> MARKER_B = ENTITIES.register("marker_b",
            () -> EntityType.Builder.of(VehicleMarkerEntity::new, MobCategory.CREATURE)
                    .sized(4.0F, 3.0F).build("marker_b"));

    public static final RegistryObject<EntityType<VehicleMarkerEntity>> MARKER_C = ENTITIES.register("marker_c",
            () -> EntityType.Builder.of(VehicleMarkerEntity::new, MobCategory.CREATURE)
                    .sized(4.0F, 3.0F).build("marker_c"));

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(MARKER_A.get(), Mob.createMobAttributes().build());
        event.put(MARKER_B.get(), Mob.createMobAttributes().build());
        event.put(MARKER_C.get(), Mob.createMobAttributes().build());
    }

    @SubscribeEvent
    public static void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(MARKER_A.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, random) -> true, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(MARKER_B.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, random) -> true, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(MARKER_C.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, random) -> true, SpawnPlacementRegisterEvent.Operation.REPLACE);
    }
}