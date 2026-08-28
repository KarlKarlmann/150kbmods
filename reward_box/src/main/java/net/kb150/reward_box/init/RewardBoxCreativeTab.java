package net.kb150.reward_box.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.kb150.reward_box.RewardBox;
import net.kb150.reward_box.util.RewardBoxConfigManager;

public class RewardBoxCreativeTab {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = 
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RewardBox.MODID);

    public static final RegistryObject<CreativeModeTab> REWARD_BOX_TAB = CREATIVE_MODE_TABS.register("reward_box_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.reward_box"))
                    .icon(() -> {
                        ItemStack iconStack = new ItemStack(RewardBoxRegistry.REWARD_BOX_ITEM.get());
                        iconStack.getOrCreateTag().putString("BoxId", "reward_box:default");
                        iconStack.getOrCreateTag().putInt("RewardTier", 1);
                        return iconStack;
                    })
                    .displayItems((parameters, output) -> {
                        // Generiert automatisch für jede geladene JSON-Kiste 3 Beispiel-Tiers!
                        for (String boxId : RewardBoxConfigManager.getLoadedBoxIds()) {
                            for (int tier : new int[]{1, 5, 10}) {
                                ItemStack stack = new ItemStack(RewardBoxRegistry.REWARD_BOX_ITEM.get());
                                CompoundTag tag = stack.getOrCreateTag();
                                tag.putString("BoxId", boxId);
                                tag.putInt("RewardTier", tier);
                                stack.setHoverName(Component.literal("Box: " + boxId + " (Tier " + tier + ")"));
                                
                                output.accept(stack);
                            }
                        }
                    })
                    .build());

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}