package net.kb150.woerp.init;

import net.kb150.woerp.Woerp;
import net.kb150.woerp.block.WoerpStoneBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Woerp.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Woerp.MODID);

    // Registrierung des Wörpstones
    public static final RegistryObject<Block> WOERP_STONE = BLOCKS.register("woerp_stone",
            () -> new WoerpStoneBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(3.0f, 6.0f).requiresCorrectToolForDrops()));

    // Zugehöriges Item, damit der Spieler ihn im Inventar halten kann
    public static final RegistryObject<Item> WOERP_STONE_ITEM = ITEMS.register("woerp_stone",
            () -> new BlockItem(WOERP_STONE.get(), new Item.Properties()));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
}