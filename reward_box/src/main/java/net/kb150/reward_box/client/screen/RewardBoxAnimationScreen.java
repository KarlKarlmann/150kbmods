package net.kb150.reward_box.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.kb150.reward_box.RewardBox;
import net.kb150.reward_box.init.RewardBoxSounds;
import net.kb150.reward_box.inventory.RewardBoxMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RewardBoxAnimationScreen extends AbstractContainerScreen<RewardBoxMenu> {
    private static final ResourceLocation CONTAINER_BACKGROUND = new ResourceLocation("textures/gui/container/generic_54.png");

    private static final ResourceLocation GLOW_COMMON = new ResourceLocation(RewardBox.MODID, "textures/gui/glow_common.png");
    private static final ResourceLocation GLOW_RARE = new ResourceLocation(RewardBox.MODID, "textures/gui/glow_rare.png");
    private static final ResourceLocation GLOW_EPIC = new ResourceLocation(RewardBox.MODID, "textures/gui/glow_epic.png");
    private static final ResourceLocation GLOW_LEGENDARY = new ResourceLocation(RewardBox.MODID, "textures/gui/glow_legendary.png");

    private long openTime;
    private boolean burstPlayed = false;
    private final Set<Integer> itemsLanded = new HashSet<>();
    private final Set<Integer> soundsPlayed = new HashSet<>();
    public final boolean playAnimation;
    
    // NEU: Wir speichern die Items zwischen, um sie aus den Vanilla-Slots zu löschen, bis sie landen
    private final NonNullList<ItemStack> hiddenItems = NonNullList.withSize(27, ItemStack.EMPTY);
    private boolean sequenceInitialized = false;
    private List<Integer> animationSequence = null;

    public RewardBoxAnimationScreen(RewardBoxMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageHeight = 114 + 3 * 18; 
        this.inventoryLabelY = this.imageHeight - 94;
        this.openTime = System.currentTimeMillis();
        this.playAnimation = menu.playAnimation;
    }

    public float getElapsedSeconds() {
        if (!this.playAnimation) return 0f;
        return (System.currentTimeMillis() - this.openTime) / 1000f;
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        if (!this.playAnimation || getElapsedSeconds() >= 2.0f) {
            super.renderBackground(graphics);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!this.playAnimation) {
            this.renderBackground(graphics);
            super.render(graphics, mouseX, mouseY, partialTicks);
            this.renderTooltip(graphics, mouseX, mouseY);
            return;
        }

        float elapsedSeconds = getElapsedSeconds();

        if (elapsedSeconds < 2.0f) {
            handleAnticipationPhase(graphics, elapsedSeconds);
        } else {
            if (!burstPlayed) {
                playLocalSound(RewardBoxSounds.BOX_BURST.get(), 1.0f, 1.0f);
                burstPlayed = true;
            }
            
            this.renderBackground(graphics);
            super.render(graphics, mouseX, mouseY, partialTicks);
            
            renderFlyingItems(graphics, elapsedSeconds - 2.0f);
            this.renderTooltip(graphics, mouseX, mouseY);
        }
    }

    private void handleAnticipationPhase(GuiGraphics graphics, float time) {
        if (time < 0.1f && !soundsPlayed.contains(-1)) {
            playLocalSound(RewardBoxSounds.BOX_CHARGE.get(), 1.0f, 0.8f);
            soundsPlayed.add(-1);
        }

        float intensity = time / 2.0f; // 0.0 bis 1.0
        int alpha = (int)(130 * intensity);
        int glowColor = (alpha << 24) | 0xFFFFFF; 
        
        graphics.fillGradient(0, 0, this.width, this.height, 0x00000000, glowColor);
    }

    private void initAnimationSequence() {
        // Prüfen, ob der Server uns schon Items geschickt hat
        boolean hasAnyItem = false;
        for (int i = 0; i < 27; i++) {
            if (!this.menu.getSlot(i).getItem().isEmpty()) {
                hasAnyItem = true;
                break;
            }
        }
        
        // Netzwerk-Lag: Server hat Items noch nicht gesendet, wir warten noch einen Frame!
        if (!hasAnyItem) return; 

        this.animationSequence = new ArrayList<>();
        
        // 1. Sammle alle belegten Slots und KLAUE sie temporär aus der GUI!
        for (int i = 0; i < 27; i++) {
            ItemStack stack = this.menu.getSlot(i).getItem();
            if (!stack.isEmpty()) {
                this.animationSequence.add(i);
                
                // Wir speichern uns eine exakte Kopie...
                this.hiddenItems.set(i, stack.copy());
                // ... und leeren den echten GUI-Slot, damit Vanilla absolut nichts anzeigt (keine Tooltips, kein Z-Fighting)!
                this.menu.getSlot(i).set(ItemStack.EMPTY);
            }
        }
        
        // 2. Gacha-Psychologie: Sortieren nach Rarity (anhand unserer versteckten Liste)
        this.animationSequence.sort((slotIndex1, slotIndex2) -> {
            ItemStack stack1 = this.hiddenItems.get(slotIndex1);
            ItemStack stack2 = this.hiddenItems.get(slotIndex2);
            return Integer.compare(stack1.getRarity().ordinal(), stack2.getRarity().ordinal());
        });
        
        this.sequenceInitialized = true;
    }

    private void renderFlyingItems(GuiGraphics graphics, float revealTime) {
        if (!this.sequenceInitialized) {
            initAnimationSequence();
            // Wenn der Server immer noch nicht geantwortet hat, abbrechen und warten
            if (!this.sequenceInitialized) return; 
        }

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // ZEICHNEN: Wir animieren die fliegenden Items strikt in der Reihenfolge
        for (int seqIndex = 0; seqIndex < this.animationSequence.size(); seqIndex++) {
            int slotIndex = this.animationSequence.get(seqIndex);
            
            // WICHTIG: Wir holen uns das Item aus UNSEREM Versteck, nicht aus dem Slot!
            ItemStack stack = this.hiddenItems.get(slotIndex);

            float itemLocalTime = revealTime - (seqIndex * 0.3f);

            // Item ist noch gar nicht dran (wartet auf seinen Einsatz)
            if (itemLocalTime < 0) continue;

            // Item ist gelandet -> Wir legen es WIEDER ZURÜCK in den Vanilla Slot!
            if (itemLocalTime >= 1.0f) {
                if (!itemsLanded.contains(slotIndex)) {
                    // Item dem echten Inventar zurückgeben, damit man es ab jetzt anklicken kann
                    this.menu.getSlot(slotIndex).set(stack.copy());
                    
                    float pitch = 1.0f + (seqIndex * 0.05f); 
                    playLocalSound(RewardBoxSounds.ITEM_LAND.get(), 0.8f, Math.min(pitch, 2.0f));
                    itemsLanded.add(slotIndex);
                }
                continue;
            }

            // Swoosh-Sound beim Startflug
            if (itemLocalTime < 0.1f && !soundsPlayed.contains(slotIndex)) {
                playLocalSound(RewardBoxSounds.ITEM_SWOOSH.get(), 0.6f, 1.0f);
                soundsPlayed.add(slotIndex);
            }

            float t = itemLocalTime; 
            
            float scale = 1.0f;
            if (t < 0.2f) {
                scale = 0.5f + (t * 2.5f); 
            } else if (t < 0.4f) {
                scale = 1.0f + Mth.sin((t - 0.2f) * (float)Math.PI / 0.2f) * 0.5f; 
            }

            int targetX = this.leftPos + 8 + (slotIndex % 9) * 18;
            int targetY = this.topPos + 18 + (slotIndex / 9) * 18;

            int controlX = centerX + (targetX - centerX) / 2;
            int controlY = centerY - 150; 

            float u = 1 - t;
            float currentX = (u * u * centerX) + (2 * u * t * controlX) + (t * t * targetX);
            float currentY = (u * u * centerY) + (2 * u * t * controlY) + (t * t * targetY);

            graphics.pose().pushPose();
            graphics.pose().translate(currentX, currentY, 250); 
            graphics.pose().scale(scale, scale, 1.0f);

            renderRarityGlow(graphics, stack, t);

            graphics.renderItem(stack, -8, -8);
            graphics.renderItemDecorations(this.font, stack, -8, -8);
            
            graphics.pose().popPose();
        }
    }

    private void renderRarityGlow(GuiGraphics graphics, ItemStack stack, float localTime) {
        ResourceLocation glowTex = GLOW_COMMON;
        Rarity rarity = stack.getRarity();

        if (rarity == Rarity.UNCOMMON) glowTex = GLOW_RARE;
        else if (rarity == Rarity.RARE) glowTex = GLOW_EPIC;
        else if (rarity == Rarity.EPIC) glowTex = GLOW_LEGENDARY;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        
        graphics.pose().pushPose();
        
        float alpha = 0.5f + 0.5f * Mth.sin(localTime * 10.0f);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        
        // Sprite-Sheet Animation für die Glows (scrollt durch 4 Frames)
        int frameCount = 4; 
        int msPerFrame = 100; 
        int frameSize = 32; 
        
        int currentFrame = (int) ((System.currentTimeMillis() / msPerFrame) % frameCount);
        int vOffset = currentFrame * frameSize;
        
        graphics.blit(glowTex, -16, -16, 0, vOffset, frameSize, frameSize, frameSize, frameSize * frameCount);
        
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.pose().popPose();
        RenderSystem.disableBlend();
    }

    private void playLocalSound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().level.playSound(
                Minecraft.getInstance().player, 
                Minecraft.getInstance().player.blockPosition(), 
                sound, 
                SoundSource.PLAYERS, 
                volume, 
                pitch
            );
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        graphics.blit(CONTAINER_BACKGROUND, x, y, 0, 0, imageWidth, 3 * 18 + 17);
        graphics.blit(CONTAINER_BACKGROUND, x, y + 3 * 18 + 17, 0, 126, imageWidth, 96);
    }
}