package net.kb150.survivorcolonies.client.gui;

import net.kb150.survivorcolonies.data.DialogManager;
import net.kb150.survivorcolonies.data.StableHash;
import net.kb150.survivorcolonies.data.SurvivorPersonality;
import net.kb150.survivorcolonies.data.dialog.PlayerOption;
import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.kb150.survivorcolonies.network.C2SDialogOptionPacket;
import net.kb150.survivorcolonies.network.ModMessages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SurvivorDialogScreen extends Screen {

    private static final ResourceLocation PAPER_BG = 
        new ResourceLocation("minecolonies", "textures/gui/citizen/colonist_paper.png");

    private final SurvivorEntity survivor;
    private final Screen parentScreen;
    
    private final int imageWidth = 190;
    private final int imageHeight = 244;
    private int leftPos;
    private int topPos;

    // Laufzeit-Zustand für den aktuellen Dialog
    private final Map<String, Integer> topicVisitCounts = new HashMap<>();
    private int currentNpcTextId = -1;
    private String activeFullText = "";
    private int visibleChars = 0;
    private long lastTickTime = 0;

    public SurvivorDialogScreen(SurvivorEntity survivor, Screen parentScreen) {
        super(Component.translatable("gui.survivorcolonies.dialog.title"));
        this.survivor = survivor;
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
        
        // Starte automatisch mit einem passenden Einstiegsthema basierend auf dem Trust-Level
        int trust = survivor.getTrust();
        String initialTopic = trust >= 70 ? "trust_high" : (trust >= 30 ? "small_talk" : "trust_low");
        
        this.loadTopic(initialTopic);
    }

    private void loadTopic(String topic) {
        UUID uuid = survivor.getUUID();
        String tone = SurvivorPersonality.getTone(uuid);
        String backstory = SurvivorPersonality.getBackstory(uuid);
        String motivation = SurvivorPersonality.getMotivation(uuid);
        
        // Mappe den echten Trust (0-100) auf die generierten Bins (1, 5, 9)
        int actualTrust = survivor.getTrust();
        int trustLevelBin = actualTrust >= 70 ? 9 : (actualTrust >= 30 ? 5 : 1);
        
        // Baue den exakten Combo-Key auf, den das Python-Skript generiert hat
        String comboKey = tone + "|" + backstory + "|" + motivation + "|" + trustLevelBin + "|" + topic;
        
        List<Integer> lines = DialogManager.getNpcLineIds(comboKey);
        
        if (lines.isEmpty()) {
            this.activeFullText = Component.translatable("gui.survivorcolonies.dialog.empty").getString();
            this.currentNpcTextId = -1;
            this.refreshButtons();
            return;
        }
        
        // Zähler für dynamische Looping-Varianz erhöhen
        int visits = this.topicVisitCounts.getOrDefault(topic, 0);
        this.topicVisitCounts.put(topic, visits + 1);
        String iterationContext = "visit_" + visits;
        
        // Deterministisch aus den verfügbaren Varianten für diesen Loop auswählen
        int variantIndex = StableHash.variantPick(lines.size(), uuid, comboKey, iterationContext);
        this.currentNpcTextId = lines.get(variantIndex);
        
        // Die Text-ID für die Lang-Datei formatieren (z.B. "survivor.text.000184")
        String langKey = String.format("survivor.text.%06d", this.currentNpcTextId);
        this.activeFullText = Component.translatable(langKey).getString();
        
        this.visibleChars = 0;
        this.lastTickTime = System.currentTimeMillis();
        
        this.refreshButtons();
    }

    private void refreshButtons() {
        this.clearWidgets();
        int btnW = 150;
        int btnX = this.leftPos + (this.imageWidth - btnW) / 2;
        int startY = this.topPos + 130;

        if (this.currentNpcTextId == -1) {
            addBtn(btnX, startY + 66, btnW, Component.translatable("gui.survivorcolonies.btn.back"), b -> returnToParent());
            return;
        }

        List<PlayerOption> options = DialogManager.getOptionsForNpcLine(this.currentNpcTextId);

        if (options.isEmpty()) {
            addBtn(btnX, startY + 66, btnW, Component.translatable("gui.survivorcolonies.btn.goodbye"), b -> returnToParent());
        } else {
            for (int i = 0; i < options.size(); i++) {
                PlayerOption opt = options.get(i);
                String optLangKey = String.format("survivor.text.%06d", opt.textId());
                
                // Prüfen, ob die Option durch den Spam-Schutz blockiert ist
                boolean isAvailable = !opt.once() || !survivor.hasUsedOption(opt.optionId());
                
                Button btn = Button.builder(Component.translatable(optLangKey), b -> {
                    // Paket an den Server senden
                    ModMessages.sendToServer(new C2SDialogOptionPacket(
                        survivor.getId(), 
                        opt.optionId(), 
                        opt.trustDelta(), 
                        opt.once()
                    ));
                    
                    // Client-Side Prediction: Option sofort als verbraucht markieren
                    if (opt.once()) {
                        survivor.markOptionUsed(opt.optionId());
                    }
                    
                    // Gespräch beenden und zum Hauptmenü zurückkehren
                    returnToParent();
                    
                }).bounds(btnX, startY + (i * 22), btnW, 18).build();
                
                // Button ausgrauen, wenn er bereits "verbraucht" wurde
                btn.active = isAvailable;
                this.addRenderableWidget(btn);
            }
        }
    }

    private void addBtn(int x, int y, int width, Component text, Button.OnPress action) {
        this.addRenderableWidget(Button.builder(text, action).bounds(x, y, width, 18).build());
    }

    private void returnToParent() {
        Minecraft.getInstance().setScreen(this.parentScreen);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        guiGraphics.blit(PAPER_BG, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
        guiGraphics.blit(getPortraitLocation(), this.leftPos + 22, this.topPos + 20, 0, 0, 32, 32, 32, 32);
        guiGraphics.drawString(this.font, this.survivor.getSurvivorName(), this.leftPos + 60, this.topPos + 22, 0x302010, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.survivorcolonies.dialog.subtitle"), this.leftPos + 60, this.topPos + 34, 0x605040, false);

        long currentTime = System.currentTimeMillis();
        if (visibleChars < activeFullText.length() && currentTime - lastTickTime > 30) {
            visibleChars++;
            lastTickTime = currentTime;
            if (this.minecraft != null && this.minecraft.player != null) {
                float pitch = survivor.isFemale() ? 1.6F : 0.9F;
                pitch += (float) (Math.random() * 0.2F - 0.1F);
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_AMBIENT, pitch, 0.15F));
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