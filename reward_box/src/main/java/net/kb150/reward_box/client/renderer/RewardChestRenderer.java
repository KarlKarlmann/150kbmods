package net.kb150.reward_box.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.kb150.reward_box.block.RewardBoxBlock;
import net.kb150.reward_box.block.entity.RewardBoxBlockEntity;
import net.kb150.reward_box.util.RewardBoxConfigManager;
import net.kb150.reward_box.init.RewardBoxRegistry;

public class RewardChestRenderer implements BlockEntityRenderer<RewardBoxBlockEntity> {
    private static final ResourceLocation DEFAULT_TEXTURE = new ResourceLocation("reward_box", "textures/entity/chest/reward_chest.png");

    private final ModelPart lid;
    private final ModelPart bottom;
    private final ModelPart lock;

    public RewardChestRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(ModelLayers.CHEST);
        this.bottom = root.getChild("bottom");
        this.lid = root.getChild("lid");
        this.lock = root.getChild("lock");
    }

    @Override
    public void render(RewardBoxBlockEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        poseStack.pushPose();

        BlockState state = entity.hasLevel() ? entity.getBlockState() : RewardBoxRegistry.REWARD_BOX_BLOCK.get().defaultBlockState();
        Direction facing = state.hasProperty(RewardBoxBlock.FACING) ? state.getValue(RewardBoxBlock.FACING) : Direction.NORTH;

        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate(-0.5D, -0.5D, -0.5D);

        float openness = entity.getOpenNess(partialTick);
        openness = 1.0F - openness;
        openness = 1.0F - openness * openness * openness;
        this.lid.xRot = -(openness * ((float) Math.PI / 2F));
        this.lock.xRot = this.lid.xRot;

        ResourceLocation texture = RewardBoxConfigManager.getTextureForBox(entity.getBoxId());
        if (texture == null) {
            texture = DEFAULT_TEXTURE;
        }

        // ==========================================
        // 1. BASIS-PASS (Normale Textur & Licht)
        // ==========================================
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityCutout(texture));

        this.bottom.render(poseStack, vertexConsumer, packedLight, packedOverlay);

        poseStack.pushPose();
        poseStack.translate(0.0D, 0.065D, 0.0D);
        this.lid.render(poseStack, vertexConsumer, packedLight, packedOverlay);
        this.lock.render(poseStack, vertexConsumer, packedLight, packedOverlay);
        poseStack.popPose();

        // ==========================================
        // 2. GLOW-PASS (Optionales Leuchten im Dunkeln)
        // ==========================================
        ResourceLocation glowTexture = RewardBoxConfigManager.getGlowTextureForBox(entity.getBoxId());
        if (glowTexture != null) {
            VertexConsumer glowConsumer = buffer.getBuffer(RenderType.eyes(glowTexture));

            this.bottom.render(poseStack, glowConsumer, LightTexture.FULL_BRIGHT, packedOverlay);

            poseStack.pushPose();
            poseStack.translate(0.0D, 0.065D, 0.0D);
            this.lid.render(poseStack, glowConsumer, LightTexture.FULL_BRIGHT, packedOverlay);
            this.lock.render(poseStack, glowConsumer, LightTexture.FULL_BRIGHT, packedOverlay);
            poseStack.popPose();
        }

        poseStack.popPose();
    }
}