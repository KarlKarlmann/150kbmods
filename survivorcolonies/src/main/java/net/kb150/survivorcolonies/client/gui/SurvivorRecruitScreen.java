package net.kb150.survivorcolonies.client.gui;

import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.kb150.survivorcolonies.network.C2SRecruitSurvivorPacket;
import net.kb150.survivorcolonies.network.ModMessages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import java.util.Random;

public class SurvivorRecruitScreen extends Screen {

    private static final ResourceLocation PAPER_BG = 
        new ResourceLocation("minecolonies", "textures/gui/citizen/colonist_paper.png");

    private final SurvivorEntity survivor;
    private final int imageWidth = 190;
    private final int imageHeight = 244;
    private int leftPos;
    private int topPos;

    // --- State-Machine & Schreibmaschinen-System ---
    private String dialogState = "main";
    private String activeFullText = "";
    private int visibleChars = 0;
    private long lastTickTime = 0;

    // Persönliche Geschichten für Fluff
    private final String personalStory;

    public SurvivorRecruitScreen(SurvivorEntity survivor) {
        super(Component.translatable("gui.survivorcolonies.recruit.title"));
        this.survivor = survivor;

        Random storyRand = new Random(survivor.getSurvivorName().hashCode());
        String[] storyKeys = {
            "dialog.survivorcolonies.recruit.story_1",
            "dialog.survivorcolonies.recruit.story_2",
            "dialog.survivorcolonies.recruit.story_3"
        };
        // Lade die Story und mache sie zu einem finalen String
        this.personalStory = Component.translatable(storyKeys[storyRand.nextInt(storyKeys.length)]).getString();
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;

        this.switchState("main");
    }

    private void switchState(String newState) {
        this.dialogState = newState;
        this.visibleChars = 0;
        this.lastTickTime = System.currentTimeMillis();
        
        switch (newState) {
            case "main" -> this.activeFullText = Component.translatable("dialog.survivorcolonies.recruit.main").getString();
            case "about" -> this.activeFullText = this.personalStory;
            case "skills" -> {
                StringBuilder sb = new StringBuilder(Component.translatable("dialog.survivorcolonies.recruit.skills_intro").getString()).append("\n");
                if (survivor.getSkills().isEmpty()) {
                    sb.append(Component.translatable("dialog.survivorcolonies.recruit.skills_none").getString());
                } else {
                    survivor.getSkills().forEach((name, lvl) -> {
                        String formattedName = name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
                        sb.append("• ").append(formattedName).append(Component.translatable("dialog.survivorcolonies.recruit.skills_level", lvl).getString()).append("\n");
                    });
                }
                this.activeFullText = sb.toString();
            }
            case "recruit_confirm" -> {
                String costName = survivor.getRecruitCost().getHoverName().getString();
                int count = survivor.getRecruitCost().getCount();
                this.activeFullText = Component.translatable("dialog.survivorcolonies.recruit.confirm_cost", count, costName).getString();
            }
        }

        this.refreshDialogButtons();
    }

	private void refreshDialogButtons() {
        this.clearWidgets();

        int btnW = 150;
        int btnX = this.leftPos + (this.imageWidth - btnW) / 2;
        int startY = this.topPos + 140;

        switch (this.dialogState) {
            case "main" -> {
                addBtn(btnX, startY, btnW, Component.translatable("gui.survivorcolonies.btn.about"), b -> switchState("about"));
                
                addBtn(btnX, startY + 22, btnW, Component.translatable("gui.survivorcolonies.btn.trade"), b -> {
                    Minecraft.getInstance().setScreen(new SurvivorTradeScreen(this.survivor, this));
                });

                addBtn(btnX, startY + 44, btnW, Component.translatable("gui.survivorcolonies.btn.skills"), b -> switchState("skills"));

                if (survivor.getTrust() >= 70) {
                    addBtn(btnX, startY + 66, btnW, Component.translatable("gui.survivorcolonies.btn.recruit"), b -> switchState("recruit_confirm"));
                    addBtn(btnX, startY + 88, btnW, Component.translatable("gui.survivorcolonies.btn.goodbye"), b -> this.onClose());
                } else {
                    addBtn(btnX, startY + 66, btnW, Component.translatable("gui.survivorcolonies.btn.goodbye"), b -> this.onClose());
                }
            }
            case "about", "skills" -> {
                addBtn(btnX, startY + 66, btnW, Component.translatable("gui.survivorcolonies.btn.back"), b -> switchState("main"));
            }
            case "recruit_confirm" -> {
                addBtn(btnX, startY + 22, btnW, Component.translatable("gui.survivorcolonies.btn.recruit_yes"), b -> {
                    ModMessages.sendToServer(new C2SRecruitSurvivorPacket(this.survivor.getId()));
                    this.onClose();
                });
                addBtn(btnX, startY + 44, btnW, Component.translatable("gui.survivorcolonies.btn.recruit_no"), b -> switchState("main"));
            }
        }
    }

    private void addBtn(int x, int y, int width, Component text, Button.OnPress action) {
        this.addRenderableWidget(Button.builder(text, action).bounds(x, y, width, 18).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        guiGraphics.blit(PAPER_BG, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
        guiGraphics.blit(getPortraitLocation(), this.leftPos + 22, this.topPos + 20, 0, 0, 32, 32, 32, 32);
        guiGraphics.drawString(this.font, this.survivor.getSurvivorName(), this.leftPos + 60, this.topPos + 22, 0x302010, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.survivorcolonies.recruit.subtitle"), this.leftPos + 60, this.topPos + 34, 0x605040, false);

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
        int textX = this.leftPos + 22;
        int textY = this.topPos + 58;
        int textWrapWidth = 146;

        guiGraphics.drawWordWrap(this.font, Component.literal("§3\"" + textToDraw + "\""), textX, textY, textWrapWidth, 0x403020);

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