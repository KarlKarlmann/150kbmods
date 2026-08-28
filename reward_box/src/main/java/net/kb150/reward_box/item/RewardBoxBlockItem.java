package net.kb150.reward_box.item;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.kb150.reward_box.block.entity.RewardBoxBlockEntity;
import net.kb150.reward_box.client.renderer.RewardBoxItemRenderer;

import java.util.function.Consumer;

public class RewardBoxBlockItem extends BlockItem {

    public RewardBoxBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

	@Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return RewardBoxItemRenderer.getInstance();
            }
        });
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, net.minecraft.world.level.Level level, Player player, ItemStack stack, BlockState state) {
        boolean superResult = super.updateCustomBlockEntityTag(pos, level, player, stack, state);

        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof RewardBoxBlockEntity boxEntity) {
            CompoundTag tag = stack.getTag();
            String boxId = "reward_box:default";
            int tier = 1;

            if (tag != null) {
                if (tag.contains("BoxId")) boxId = tag.getString("BoxId");
                if (tag.contains("RewardTier")) tier = tag.getInt("RewardTier");
            }

            boxEntity.setBoxData(boxId, tier);
        }
        return superResult;
    }
}