package net.kb150.survivorcolonies.client.gui;

import net.kb150.survivorcolonies.data.SurvivorValueCalculator;
import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.kb150.survivorcolonies.network.C2STradeOfferPacket;
import net.kb150.survivorcolonies.network.ModMessages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SurvivorTradeScreen extends Screen {

    private static final ResourceLocation PAPER_BG = 
        new ResourceLocation("minecolonies", "textures/gui/citizen/colonist_paper.png");

    private final SurvivorEntity survivor;
    private final Screen parentScreen;
    
    private final int imageWidth = 190;
    private final int imageHeight = 244;
    private int leftPos;
    private int topPos;

    // UI-Animation & Zustand
    private String activeFullText = "";
    private int visibleChars = 0;
    private long lastTickTime = 0;
    private String tradeState = "survivor_offers"; 

    // Barter-Daten
    private final List<Integer> survivorInventorySlots = new ArrayList<>();
    private List<ItemStack> playerTradeCandidates = new ArrayList<>();
    
    private int selectedSurvivorSlot = -1;
    private ItemStack selectedSurvivorLoot = ItemStack.EMPTY;
    private ItemStack selectedPlayerOffer = ItemStack.EMPTY;
    
    private int offerAmount = 1;
    private int requiredAmount = 1; 

    public SurvivorTradeScreen(SurvivorEntity survivor, Screen parentScreen) {
        super(Component.translatable("gui.survivorcolonies.trade.title"));
        this.survivor = survivor;
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
        this.switchState("survivor_offers");
    }

    private void scanPlayerInventory() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        List<ItemStack> candidates = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && !stack.isDamageableItem()) {
                int value = SurvivorValueCalculator.getItemValue(stack, survivor);
                if (value > 0) {
                    boolean alreadyContains = candidates.stream().anyMatch(c -> ItemStack.isSameItem(c, stack));
                    if (!alreadyContains) {
                        candidates.add(stack.copy());
                    }
                }
            }
        }
        Collections.shuffle(candidates);
        this.playerTradeCandidates = candidates.subList(0, Math.min(3, candidates.size()));
    }

    private void switchState(String newState) {
        this.tradeState = newState;
        this.visibleChars = 0;
        this.lastTickTime = System.currentTimeMillis();

        switch (newState) {
            case "survivor_offers" -> {
                this.survivorInventorySlots.clear();
                for (int i = 0; i < survivor.getInventory().getContainerSize(); i++) {
                    if (!survivor.getInventory().getItem(i).isEmpty()) {
                        this.survivorInventorySlots.add(i);
                    }
                }
                if (this.survivorInventorySlots.isEmpty()) {
                    this.activeFullText = Component.translatable("dialog.survivorcolonies.trade.empty").getString();
                } else {
                    this.activeFullText = Component.translatable("dialog.survivorcolonies.trade.has_items").getString();
                }
            }
            case "player_offers" -> {
                this.scanPlayerInventory();
                if (this.playerTradeCandidates.isEmpty()) {
                    this.activeFullText = Component.translatable("dialog.survivorcolonies.trade.player_empty").getString();
                } else {
                    String lootName = this.selectedSurvivorLoot.getHoverName().getString();
                    this.activeFullText = Component.translatable("dialog.survivorcolonies.trade.player_offer", lootName).getString();
                }
            }
            case "haggle" -> {
                int survivorValue = SurvivorValueCalculator.getSurvivorSaleValue(this.selectedSurvivorLoot, survivor);
                int singlePlayerValue = SurvivorValueCalculator.getItemValue(this.selectedPlayerOffer, survivor);
                
                this.requiredAmount = (int) Math.ceil((double) survivorValue / Math.max(1, singlePlayerValue));
                int currentOfferValue = this.offerAmount * singlePlayerValue;
                String itemName = this.selectedPlayerOffer.getHoverName().getString();

                if (currentOfferValue < survivorValue) {
                    this.activeFullText = Component.translatable("dialog.survivorcolonies.trade.haggle_reject", this.offerAmount, itemName).getString();
                } else {
                    this.activeFullText = Component.translatable("dialog.survivorcolonies.trade.haggle_accept", this.offerAmount, itemName).getString();
                }
            }
            case "done" -> {
                this.activeFullText = Component.translatable("dialog.survivorcolonies.trade.done").getString();
            }
        }
        this.refreshTradeButtons();
    }

    private void refreshTradeButtons() {
        this.clearWidgets();

        int btnW = 150;
        int btnX = this.leftPos + (this.imageWidth - btnW) / 2;
        int startY = this.topPos + 120;

        switch (this.tradeState) {
            case "survivor_offers" -> {
                if (this.survivorInventorySlots.isEmpty()) {
                    addBtn(btnX, startY + 66, btnW, Component.translatable("gui.survivorcolonies.btn.back"), b -> returnToParent());
                } else {
                    int maxItems = Math.min(3, this.survivorInventorySlots.size());
                    for (int i = 0; i < maxItems; i++) {
                        int slot = this.survivorInventorySlots.get(i);
                        ItemStack loot = survivor.getInventory().getItem(slot);
                        addBtn(btnX, startY + (i * 22), btnW, Component.literal(loot.getCount() + "x ").append(loot.getHoverName()), b -> {
                            this.selectedSurvivorSlot = slot;
                            this.selectedSurvivorLoot = loot;
                            this.switchState("player_offers");
                        });
                    }
                    addBtn(btnX, startY + 77, btnW, Component.translatable("gui.survivorcolonies.btn.back"), b -> returnToParent());
                }
            }
            case "player_offers" -> {
                if (this.playerTradeCandidates.isEmpty()) {
                    addBtn(btnX, startY + 66, btnW, Component.translatable("gui.survivorcolonies.btn.cancel"), b -> switchState("survivor_offers"));
                } else {
                    for (int i = 0; i < this.playerTradeCandidates.size(); i++) {
                        ItemStack candidate = this.playerTradeCandidates.get(i);
                        addBtn(btnX, startY + (i * 22), btnW, Component.translatable("gui.survivorcolonies.trade.offer_item", candidate.getHoverName()), b -> {
                            this.selectedPlayerOffer = candidate;
                            this.offerAmount = 1;
                            this.switchState("haggle");
                        });
                    }
                    addBtn(btnX, startY + 77, btnW, Component.translatable("gui.survivorcolonies.btn.cancel"), b -> switchState("survivor_offers"));
                }
            }
            case "haggle" -> {
                Component btnText = Component.translatable("gui.survivorcolonies.trade.amount", this.offerAmount);
                
                addBtn(btnX, startY, 30, Component.literal("-"), b -> {
                    if (this.offerAmount > 1) {
                        this.offerAmount--;
                        this.switchState("haggle");
                    }
                });
                
                addBtn(btnX + 35, startY, 80, btnText, b -> {}); 
                
                addBtn(btnX + 120, startY, 30, Component.literal("+"), b -> {
                    Player player = Minecraft.getInstance().player;
                    int maxAmount = player != null ? getPlayerItemCount(player, this.selectedPlayerOffer) : 64;
                    if (this.offerAmount < maxAmount) {
                        this.offerAmount++;
                        this.switchState("haggle");
                    }
                });

                if (this.offerAmount >= this.requiredAmount) {
                    addBtn(btnX, startY + 33, btnW, Component.translatable("gui.survivorcolonies.trade.finish"), b -> {
                        ItemStack offerStack = this.selectedPlayerOffer.copy();
                        offerStack.setCount(this.offerAmount);
                        ModMessages.sendToServer(new C2STradeOfferPacket(this.survivor.getId(), this.selectedSurvivorSlot, offerStack));
                        this.switchState("done");
                    });
                }

                addBtn(btnX, startY + 66, btnW, Component.translatable("gui.survivorcolonies.trade.choose_other"), b -> switchState("player_offers"));
            }
            case "done" -> {
                addBtn(btnX, startY + 77, btnW, Component.translatable("gui.survivorcolonies.btn.back"), b -> returnToParent());
            }
        }
    }

    private void addBtn(int x, int y, int width, Component text, Button.OnPress action) {
        this.addRenderableWidget(Button.builder(text, action).bounds(x, y, width, 18).build());
    }

    private void returnToParent() {
        Minecraft.getInstance().setScreen(this.parentScreen);
    }

    private int getPlayerItemCount(Player player, ItemStack targetStack) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (ItemStack.isSameItem(stack, targetStack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        guiGraphics.blit(PAPER_BG, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
        guiGraphics.blit(getPortraitLocation(), this.leftPos + 22, this.topPos + 20, 0, 0, 32, 32, 32, 32);
        guiGraphics.drawString(this.font, this.survivor.getSurvivorName(), this.leftPos + 60, this.topPos + 22, 0x302010, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.survivorcolonies.trade.subtitle"), this.leftPos + 60, this.topPos + 34, 0x605040, false);

        long currentTime = System.currentTimeMillis();
        if (visibleChars < activeFullText.length() && currentTime - lastTickTime > 30) {
            visibleChars++;
            lastTickTime = currentTime;
            if (this.minecraft != null && this.minecraft.player != null) {
                float pitch = survivor.isFemale() ? 1.6F : 0.9F;
                pitch += (float) (Math.random() * 0.2F - 0.1F);
                this.minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.VILLAGER_AMBIENT, pitch, 0.15F)
                );
            }
        }

        String textToDraw = activeFullText.substring(0, Math.min(visibleChars, activeFullText.length()));
        guiGraphics.drawWordWrap(this.font, Component.literal("§3\"" + textToDraw + "\""), this.leftPos + 22, this.topPos + 58, 146, 0x403020);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private ResourceLocation getPortraitLocation() {
        String gender = survivor.isFemale() ? "female" : "male";
        String suffix = survivor.getTextureSuffix();
        return new ResourceLocation("minecolonies", "textures/entity_icon/citizen/default/knight" + gender + "1" + suffix + ".png");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}