package net.kb150.reward_box.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.kb150.reward_box.block.RewardBoxBlock;
import net.kb150.reward_box.block.entity.RewardBoxBlockEntity;
import net.kb150.reward_box.util.RewardBoxConfigManager;
import net.kb150.reward_box.init.RewardBoxRegistry;

import java.util.List;

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

        float animTime = entity.clientAnimationTick > 0 ? entity.clientAnimationTick + partialTick : 0;

        // Anticipation Shake (Ticks 1 to 40)
        // WICHTIG: Das passiert jetzt global im World-Space! Jeder sieht die Truhe wackeln.
        if (animTime > 0 && animTime < 40.0f) {
            float intensity = animTime / 40.0f; 
            float shakeX = (float) Math.sin(animTime * 1.5f) * 0.05f * intensity;
            float shakeY = (float) Math.cos(animTime * 1.8f) * 0.05f * intensity;
            float shakeZ = (float) Math.sin(animTime * 1.3f) * 0.05f * intensity;
            poseStack.translate(shakeX, shakeY, shakeZ);
        }

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
        if (texture == null) texture = DEFAULT_TEXTURE;

        // Die Truhe selbst wird normal beleuchtet (nimmt das Umgebungslicht packedLight an)
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityCutout(texture));
        this.bottom.render(poseStack, vertexConsumer, packedLight, packedOverlay);

        poseStack.pushPose();
        poseStack.translate(0.0D, 0.065D, 0.0D);
        this.lid.render(poseStack, vertexConsumer, packedLight, packedOverlay);
        this.lock.render(poseStack, vertexConsumer, packedLight, packedOverlay);
        poseStack.popPose();
        
        poseStack.popPose();

        // 3D Parabel-Animation (Herausfliegen und Wieder-Einsaugen)
        if (animTime >= 40.0f) {
            renderFloatingItems(entity, animTime, poseStack, buffer, packedLight, packedOverlay);
        }
    }

    private void renderFloatingItems(RewardBoxBlockEntity entity, float animTime, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        List<ItemStack> items = entity.getFlattenedRenderItems(); 
        if (items.isEmpty()) return;

        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();

        // HIER IST DIE MAGIE: Wir zwingen die Items, maximal zu leuchten (Block- und Sky-Light auf 15)!
        int magicalFullbright = LightTexture.FULL_BRIGHT; 

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D); // Startpunkt tief im Inneren der Kiste

        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            
            float startTick = 40.0f + (i * 2.0f); 
            float itemTime = animTime - startTick;
            
            // Render NUR WÄHREND der 50 Ticks Flugzeit! Davor und danach ist es in der Box unsichtbar.
            if (itemTime < 0 || itemTime > 50.0f) continue; 
            
            // p geht von 0.0 (Start in Box) zu 0.5 (Apex/Höhepunkt) zu 1.0 (Zurück in Box)
            float p = itemTime / 50.0f; 
            
            poseStack.pushPose();
            
            // --- Seltenheits-Modifikatoren ---
            net.minecraft.world.item.Rarity rarity = stack.getRarity();
            float rarityHeight = 1.0f;
            float rarityScale = 1.0f;
            
            if (rarity == net.minecraft.world.item.Rarity.UNCOMMON) { rarityHeight = 1.5f; rarityScale = 1.4f; }
            else if (rarity == net.minecraft.world.item.Rarity.RARE) { rarityHeight = 2.2f; rarityScale = 1.8f; }
            else if (rarity == net.minecraft.world.item.Rarity.EPIC) { rarityHeight = 3.5f; rarityScale = 2.5f; }
            
            // DIE MAGISCHE MATHEMATIK (Sinuskurve für die Parabel)
            // arc ist bei p=0 (Start) -> 0, bei p=0.5 (Mitte) -> 1, bei p=1.0 (Ende) -> 0
            float arc = (float) Math.sin(p * Math.PI); 
            
            // 1. Es fliegt hoch und kommt wieder runter
            float height = arc * rarityHeight; 
            
            // 2. Es drückt sich nach außen weg und wird wieder eingesaugt
            float radius = arc * 1.5f; 
            float angle = (i * 2.4f) + (itemTime * 0.15f); // Dreht sich um die Truhe
            
            float x = (float) Math.cos(angle) * radius;
            float z = (float) Math.sin(angle) * radius;
            
            poseStack.translate(x, height, z);
            
            // 3. Jedes Item dreht sich in der Luft wild um sich selbst (wie ein Collectible)
            poseStack.mulPose(Axis.YP.rotationDegrees(itemTime * 20.0f));
            
            // 4. Es ist winzig beim Rausfliegen, wird RIESIG am Höhepunkt, und schrumpft wieder beim Einsaugen!
            float currentScale = arc * rarityScale;
            // Wir verhindern, dass es GANZ unsichtbar wird, indem wir ein Minimum setzen
            currentScale = Math.max(0.01f, currentScale); 
            poseStack.scale(currentScale, currentScale, currentScale);
            
            // Hier übergeben wir unseren 'magicalFullbright' statt des normalen 'packedLight'!
            itemRenderer.renderStatic(stack, ItemDisplayContext.GROUND, magicalFullbright, packedOverlay, poseStack, buffer, entity.getLevel(), i);
            poseStack.popPose();
        }
        poseStack.popPose();
    }
}