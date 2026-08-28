package net.kb150.reward_box.init;

import net.kb150.reward_box.RewardBox;
import net.kb150.reward_box.block.RewardBoxBlock;
import net.kb150.reward_box.block.entity.RewardBoxBlockEntity;
import net.kb150.reward_box.item.RewardBoxBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class RewardBoxRegistry {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, RewardBox.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RewardBox.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, RewardBox.MODID);

    public static final RegistryObject<Block> REWARD_BOX_BLOCK = BLOCKS.register("reward_chest", RewardBoxBlock::new);

    public static final RegistryObject<Item> REWARD_BOX_ITEM = ITEMS.register("reward_chest", 
            () -> new RewardBoxBlockItem(REWARD_BOX_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<RewardBoxBlockEntity>> REWARD_BOX_BE = BLOCK_ENTITIES.register("reward_chest",
            () -> BlockEntityType.Builder.of(RewardBoxBlockEntity::new, REWARD_BOX_BLOCK.get()).build(null));

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
    }
}