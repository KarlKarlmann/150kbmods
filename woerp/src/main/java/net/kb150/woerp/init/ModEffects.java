package net.kb150.woerp.init;

import net.kb150.woerp.Woerp;
import net.kb150.woerp.effect.WoerpMutationEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

public class ModEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Woerp.MODID);

    public static final RegistryObject<MobEffect> MUTATION_HORN = MOB_EFFECTS.register("mutation_horn",
            () -> new WoerpMutationEffect(0xE6E600)
                    .addAttributeModifier(Attributes.ATTACK_DAMAGE, "d1a0e101-1111-4000-8000-000000000001", 2.5, AttributeModifier.Operation.ADDITION));

    public static final RegistryObject<MobEffect> MUTATION_EYESTALK = MOB_EFFECTS.register("mutation_eyestalk",
            () -> new WoerpMutationEffect(0xCCFF00)
                    .addAttributeModifier(Attributes.FOLLOW_RANGE, "d1a0e102-2222-4000-8000-000000000002", 12.0, AttributeModifier.Operation.ADDITION));

    public static final RegistryObject<MobEffect> MUTATION_VISAGE = MOB_EFFECTS.register("mutation_visage",
            () -> new WoerpMutationEffect(0xFF9900)
                    .addAttributeModifier(Attributes.ARMOR_TOUGHNESS, "d1a0e103-3333-4000-8000-000000000003", 1.5, AttributeModifier.Operation.ADDITION));

    public static final RegistryObject<MobEffect> MUTATION_SECOND_FACE = MOB_EFFECTS.register("mutation_second_face",
            () -> new WoerpMutationEffect(0xE6E600)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, "d1a0e104-4444-4000-8000-000000000004", 0.08, AttributeModifier.Operation.MULTIPLY_BASE));

    public static final RegistryObject<MobEffect> MUTATION_PUSTULES = MOB_EFFECTS.register("mutation_pustules",
            () -> new WoerpMutationEffect(0xFF9900)
                    .addAttributeModifier(Attributes.MAX_HEALTH, "d1a0e105-5555-4000-8000-000000000005", 6.0, AttributeModifier.Operation.ADDITION));

    public static final RegistryObject<MobEffect> MUTATION_CREST = MOB_EFFECTS.register("mutation_crest",
            () -> new WoerpMutationEffect(0xCCFF00)
                    .addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, "d1a0e106-6666-4000-8000-000000000006", 0.20, AttributeModifier.Operation.ADDITION));

    public static final RegistryObject<MobEffect> MUTATION_SPUR = MOB_EFFECTS.register("mutation_spur",
            () -> new WoerpMutationEffect(0xE6E600)
                    .addAttributeModifier(Attributes.ATTACK_DAMAGE, "d1a0e107-7777-4000-8000-000000000007", 1.5, AttributeModifier.Operation.ADDITION)
                    .addAttributeModifier(Attributes.ARMOR, "d1a0e107-7777-4000-8000-000000000008", 1.0, AttributeModifier.Operation.ADDITION));

    public static final RegistryObject<MobEffect> MUTATION_CARAPACE = MOB_EFFECTS.register("mutation_carapace",
            () -> new WoerpMutationEffect(0xFF9900)
                    .addAttributeModifier(Attributes.ARMOR, "d1a0e108-8888-4000-8000-000000000009", 3.0, AttributeModifier.Operation.ADDITION));

    public static final RegistryObject<MobEffect> MUTATION_CLUBFOOT = MOB_EFFECTS.register("mutation_clubfoot",
            () -> new WoerpMutationEffect(0xCCFF00)
                    .addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, "d1a0e109-9999-4000-8000-000000000010", 0.30, AttributeModifier.Operation.ADDITION));

    public static final RegistryObject<MobEffect> MUTATION_MAW = MOB_EFFECTS.register("mutation_maw",
            () -> new WoerpMutationEffect(0xE6E600)
                    .addAttributeModifier(Attributes.ATTACK_DAMAGE, "d1a0e110-aaaa-4000-8000-000000000011", 3.0, AttributeModifier.Operation.ADDITION));

    public static final List<RegistryObject<MobEffect>> ALL_MUTATIONS = List.of(
            MUTATION_HORN, MUTATION_EYESTALK, MUTATION_VISAGE, MUTATION_SECOND_FACE, MUTATION_PUSTULES,
            MUTATION_CREST, MUTATION_SPUR, MUTATION_CARAPACE, MUTATION_CLUBFOOT, MUTATION_MAW
    );

    public static void register(IEventBus bus) {
        MOB_EFFECTS.register(bus);
    }
}