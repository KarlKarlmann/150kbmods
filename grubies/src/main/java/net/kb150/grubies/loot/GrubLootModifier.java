package net.kb150.grubies.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.kb150.grubies.GrubiesMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class GrubLootModifier extends LootModifier {
    public static final Supplier<Codec<GrubLootModifier>> CODEC = () ->
            RecordCodecBuilder.create(inst -> codecStart(inst).and(
                    Codec.FLOAT.fieldOf("drop_chance").forGetter(m -> m.dropChance)
            ).apply(inst, GrubLootModifier::new));

    private final float dropChance;

    public GrubLootModifier(LootItemCondition[] conditionsIn, float dropChance) {
        super(conditionsIn);
        this.dropChance = dropChance;
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        BlockState state = context.getParamOrNull(LootContextParams.BLOCK_STATE);
        ItemStack tool = context.getParamOrNull(LootContextParams.TOOL);

        if (state == null || tool == null) return generatedLoot;

        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, tool) > 0) return generatedLoot;

        if (tool.getItem() instanceof ShovelItem && state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            boolean dropped = context.getRandom().nextFloat() < this.dropChance;
            
            GrubiesMod.debug("LOOT", "Block: {} | Tool: {} | Drop: {}", 
                    ForgeRegistries.BLOCKS.getKey(state.getBlock()), 
                    ForgeRegistries.ITEMS.getKey(tool.getItem()), 
                    dropped);

            if (dropped) {
                ItemStack grub = new ItemStack(GrubiesMod.GRUB.get());
                String blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString();
                grub.getOrCreateTag().putString("OriginBlock", blockId);
                generatedLoot.add(grub);
            }
        }
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}