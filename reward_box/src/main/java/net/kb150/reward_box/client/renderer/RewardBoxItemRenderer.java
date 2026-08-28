package net.kb150.reward_box.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.kb150.reward_box.block.entity.RewardBoxBlockEntity;
import net.kb150.reward_box.init.RewardBoxRegistry;

public class RewardBoxItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static RewardBoxItemRenderer INSTANCE;
    private RewardBoxBlockEntity dummyEntity;

    public RewardBoxItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static RewardBoxItemRenderer getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new RewardBoxItemRenderer();
        }
        return INSTANCE;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (this.dummyEntity == null) {
            this.dummyEntity = new RewardBoxBlockEntity(BlockPos.ZERO, RewardBoxRegistry.REWARD_BOX_BLOCK.get().defaultBlockState());
        }

        CompoundTag tag = stack.getTag();
        String boxId = (tag != null && tag.contains("BoxId")) ? tag.getString("BoxId") : "reward_box:default";
        int tier = (tag != null && tag.contains("RewardTier")) ? tag.getInt("RewardTier") : 1;

        this.dummyEntity.setBoxData(boxId, tier);
        Minecraft.getInstance().getBlockEntityRenderDispatcher().renderItem(this.dummyEntity, poseStack, buffer, packedLight, packedOverlay);
    }
}