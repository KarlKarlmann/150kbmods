package net.kb150.survivorcolonies.client.gui;

import com.mojang.math.Axis;
import net.kb150.survivorcolonies.data.DialogManager;
import net.kb150.survivorcolonies.data.StableHash;
import net.kb150.survivorcolonies.data.SurvivorPersonality;
import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.kb150.survivorcolonies.network.C2SDialogOptionPacket;
import net.kb150.survivorcolonies.network.ModMessages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pergament-Screen für Dialoge mit Typemachine-Effekt, Tonfall-Resume-Präfix,
 * Beziehungsstatus-Header und Vertrauens-Popup-Partikeln.
 */
public class SurvivorDialogScreen extends Screen {

    private static final ResourceLocation PAPER_BG =
            new ResourceLocation("minecolonies", "textures/gui/citizen/colonist_paper.png");

    private final SurvivorEntity survivor;
    private final Screen parentScreen;

    private final int imageWidth = 190;
    private final int imageHeight = 244;
    private int leftPos;
    private int topPos;

    // Aktueller NPC-Reaktionsknoten
    private int currentNpcReactionId = -1;
    private String activeFullText = "";
    private int visibleChars = 0;
    private long lastTickTime = 0;

    // Karussell-Index für Spieleroptionen
    private int currentOptionIndex = 0;

    // Schwebende Vertrauens-Animationen
    private final List<TrustPopup> trustPopups = new ArrayList<>();
    private static final long TRUST_POPUP_DURATION_MS = 900L;

    private static final class TrustPopup {
        final float startX;
        final float startY;
        final int delta;
        final long startTime;

        TrustPopup(float startX, float startY, int delta) {
            this.startX = startX;
            this.startY = startY;
            this.delta = delta;
            this.startTime = System.currentTimeMillis();
        }
    }

    private final class CarouselArrowButton extends Button {
        private final String arrowSymbol;

        CarouselArrowButton(int x, int y, int width, int height, String symbol, Button.OnPress onPress) {
            super(x, y, width, height, Component.literal(symbol), onPress, DEFAULT_NARRATION);
            this.arrowSymbol = symbol;
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = this.isHoveredOrFocused();
            int bgColor = hovered ? 0x428A5520 : 0x18402810;
            int borderColor = hovered ? 0xB08A5520 : 0x30402810;

            guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);
            guiGraphics.renderOutline(this.getX(), this.getY(), this.width, this.height, borderColor);

            int arrowColor = hovered ? 0x1A0A00 : 0x5C381E;
            int textWidth = SurvivorDialogScreen.this.font.width(this.arrowSymbol);
            int textX = this.getX() + (this.width - textWidth) / 2;
            int textY = this.getY() + (this.height - 8) / 2;

            guiGraphics.drawString(SurvivorDialogScreen.this.font, this.arrowSymbol, textX, textY, arrowColor, false);
        }
    }

    private final class ParchmentButton extends Button {
        ParchmentButton(int x, int y, int width, int height, Component text, Button.OnPress onPress) {
            super(x, y, width, height, text, onPress, DEFAULT_NARRATION);
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = this.isHoveredOrFocused();
            int bgColor = hovered ? 0x428A5520 : 0x18402810;
            int borderColor = hovered ? 0xB08A5520 : 0x30402810;

            guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);
            guiGraphics.renderOutline(this.getX(), this.getY(), this.width, this.height, borderColor);

            int textColor = hovered ? 0x1A0A00 : 0x4A2E16;
            int textWidth = SurvivorDialogScreen.this.font.width(this.getMessage());
            int textX = this.getX() + (this.width - textWidth) / 2;
            int textY = this.getY() + (this.height - 8) / 2;

            guiGraphics.drawString(SurvivorDialogScreen.this.font, this.getMessage(), textX, textY, textColor, false);
        }
    }

    private final class DialogOptionCardButton extends Button {
        private final List<FormattedCharSequence> wrappedLines;
        private final int optionIndex;
        private final int totalOptions;
        private final String styleLabel;

        DialogOptionCardButton(
                int x,
                int y,
                int width,
                int height,
                int index,
                int total,
                String style,
                Component text,
                Button.OnPress onPress
        ) {
            super(x, y, width, height, text, onPress, DEFAULT_NARRATION);
            this.optionIndex = index;
            this.totalOptions = total;
            this.styleLabel = style;
            this.wrappedLines = SurvivorDialogScreen.this.font.split(text, width - 8);
            this.setTooltip(Tooltip.create(text));
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = this.isHoveredOrFocused();

            int bgColor = hovered ? 0x3E8A5520 : 0x18402810;
            int borderColor = hovered ? 0xB58A5520 : 0x3A402810;

            guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);
            guiGraphics.renderOutline(this.getX(), this.getY(), this.width, this.height, borderColor);

            String headerText = (this.optionIndex + 1) + " / " + this.totalOptions;
            if (this.styleLabel != null && !this.styleLabel.isEmpty()) {
                headerText += " • " + this.styleLabel;
            }
            int headerColor = hovered ? 0x8C3B00 : 0x6E4A28;
            guiGraphics.drawString(SurvivorDialogScreen.this.font, headerText, this.getX() + 5, this.getY() + 4, headerColor, false);

            int dividerColor = hovered ? 0x608A5520 : 0x22402810;
            guiGraphics.fill(this.getX() + 4, this.getY() + 14, this.getX() + this.width - 4, this.getY() + 15, dividerColor);

            int textColor = hovered ? 0x150700 : 0x301C10;
            int lineY = this.getY() + 18;
            int textX = this.getX() + 5;

            for (FormattedCharSequence line : this.wrappedLines) {
                if (lineY + 8 > this.getY() + this.height - 2) {
                    break;
                }
                guiGraphics.drawString(SurvivorDialogScreen.this.font, line, textX, lineY, textColor, false);
                lineY += 9;
            }
        }
    }

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

        UUID playerUuid = (this.minecraft != null && this.minecraft.player != null)
                ? this.minecraft.player.getUUID()
                : null;

        // 1. Gespeicherten Gesprächsstand für diesen Spieler laden
        int savedState = (playerUuid != null) ? this.survivor.getDialogState(playerUuid) : -1;

        if (savedState > 0 && DialogManager.getNpcReaction(savedState) != null) {
            showResumedNpcReaction(savedState);
            return;
        }

        // 2. Erstes Treffen: Deterministischen Root ansteuern
        List<Integer> roots = DialogManager.getRoots();
        if (roots == null || roots.isEmpty()) {
            showEmptyDialog();
            return;
        }

        UUID survivorUuid = this.survivor.getUUID();
        int rootIndex = StableHash.variantPick(
                roots.size(),
                survivorUuid,
                "dialog_roots",
                "start"
        );

        int initialNodeId = roots.get(rootIndex);
        if (playerUuid != null) {
            this.survivor.setDialogState(playerUuid, initialNodeId);
        }

        showNpcReaction(initialNodeId);
    }

    private Component getFormattedText(String textOrKey) {
        String playerName = (this.minecraft != null && this.minecraft.player != null)
                ? this.minecraft.player.getName().getString()
                : "Survivor";
        String selfName = this.survivor.getSurvivorName();
        String home = SurvivorPersonality.getDynamicLocation(this.survivor.getUUID(), this.survivor.level());
        String monster = SurvivorPersonality.getDynamicMonster(this.survivor.getUUID());

        Component translatable = Component.translatable(textOrKey, playerName, selfName, home, monster);

        String resolved = translatable.getString();
        if (resolved.contains("<PLAYER>") || resolved.contains("<SELF>")
                || resolved.contains("<HOME>") || resolved.contains("<MONSTER>")) {
            resolved = resolved
                    .replace("<PLAYER>", playerName)
                    .replace("<SELF>", selfName)
                    .replace("<HOME>", home)
                    .replace("<MONSTER>", monster);
            return Component.literal(resolved);
        }

        return translatable;
    }

    private void showNpcReaction(int reactionId) {
        DialogManager.NpcReaction reaction = DialogManager.getNpcReaction(reactionId);
        if (reaction == null) {
            showEmptyDialog();
            return;
        }

        this.currentNpcReactionId = reactionId;
        this.currentOptionIndex = 0;
        this.activeFullText = getFormattedText(reaction.text()).getString();
        this.visibleChars = 0;
        this.lastTickTime = System.currentTimeMillis();
        refreshButtons();
    }

    private void showResumedNpcReaction(int reactionId) {
        DialogManager.NpcReaction reaction = DialogManager.getNpcReaction(reactionId);
        if (reaction == null) {
            showEmptyDialog();
            return;
        }

        String playerName = (this.minecraft != null && this.minecraft.player != null)
                ? this.minecraft.player.getName().getString()
                : "Survivor";

        Component prefixComponent = SurvivorPersonality.getResumePrefix(
                this.survivor.getUUID(),
                this.survivor.getTrust(),
                playerName
        );

        this.currentNpcReactionId = reactionId;
        this.currentOptionIndex = 0;
        this.activeFullText = prefixComponent.getString() + getFormattedText(reaction.text()).getString();
        this.visibleChars = 0;
        this.lastTickTime = System.currentTimeMillis();
        refreshButtons();
    }

    private void showEmptyDialog() {
        this.currentNpcReactionId = -1;
        this.currentOptionIndex = 0;
        this.activeFullText = Component.translatable("gui.survivorcolonies.dialog.empty").getString();
        this.visibleChars = 0;
        this.lastTickTime = System.currentTimeMillis();
        refreshButtons();
    }

    private void refreshButtons() {
        this.clearWidgets();

        int innerX = this.leftPos + 18;
        int usableWidth = 154;

        if (this.currentNpcReactionId == -1) {
            addBottomButton(
                    innerX,
                    this.topPos + 220,
                    usableWidth,
                    Component.translatable("gui.survivorcolonies.btn.back"),
                    b -> returnToParent()
            );
            return;
        }

        DialogManager.NpcReaction npcReaction = DialogManager.getNpcReaction(this.currentNpcReactionId);

        if (npcReaction == null || npcReaction.options().isEmpty()) {
            addBottomButton(
                    innerX,
                    this.topPos + 220,
                    usableWidth,
                    Component.translatable("gui.survivorcolonies.btn.goodbye"),
                    b -> returnToParent()
            );
            return;
        }

        List<Integer> optionIds = npcReaction.options();
        int totalOptions = optionIds.size();

        if (this.currentOptionIndex >= totalOptions) {
            this.currentOptionIndex = 0;
        } else if (this.currentOptionIndex < 0) {
            this.currentOptionIndex = totalOptions - 1;
        }

        int arrowWidth = 14;
        int spacing = 3;
        int cardWidth = usableWidth - (arrowWidth * 2) - (spacing * 2);
        int cardHeight = 96;
        int startY = this.topPos + 118;

        if (totalOptions > 1) {
            this.addRenderableWidget(new CarouselArrowButton(
                    innerX,
                    startY,
                    arrowWidth,
                    cardHeight,
                    "◀",
                    b -> {
                        this.currentOptionIndex = (this.currentOptionIndex - 1 + totalOptions) % totalOptions;
                        this.refreshButtons();
                    }
            ));
        }

        int currentOptionId = optionIds.get(this.currentOptionIndex);
        DialogManager.PlayerReaction playerReaction = DialogManager.getPlayerReaction(currentOptionId);

        if (playerReaction != null) {
            Component formattedOption = getFormattedText(playerReaction.text());
            int cardX = totalOptions > 1 ? innerX + arrowWidth + spacing : innerX;
            int actualCardWidth = totalOptions > 1 ? cardWidth : usableWidth;

            this.addRenderableWidget(new DialogOptionCardButton(
                    cardX,
                    startY,
                    actualCardWidth,
                    cardHeight,
                    this.currentOptionIndex,
                    totalOptions,
                    playerReaction.style(),
                    formattedOption,
                    b -> choosePlayerReaction(currentOptionId, b)
            ));
        }

        if (totalOptions > 1) {
            this.addRenderableWidget(new CarouselArrowButton(
                    innerX + arrowWidth + spacing + cardWidth + spacing,
                    startY,
                    arrowWidth,
                    cardHeight,
                    "▶",
                    b -> {
                        this.currentOptionIndex = (this.currentOptionIndex + 1) % totalOptions;
                        this.refreshButtons();
                    }
            ));
        }

        addBottomButton(
                innerX,
                this.topPos + 220,
                usableWidth,
                Component.translatable("gui.survivorcolonies.btn.goodbye"),
                b -> returnToParent()
        );
    }

    private void choosePlayerReaction(int playerReactionId, Button clickedButton) {
        DialogManager.NpcReaction next = DialogManager.resolvePlayerReaction(this.survivor, playerReactionId);
        if (next == null) {
            return;
        }

        ModMessages.sendToServer(new C2SDialogOptionPacket(
                this.survivor.getId(),
                this.currentNpcReactionId,
                playerReactionId,
                next.id()
        ));

        // Clientseitig direkt den Dialogstatus aktualisieren
        if (this.minecraft != null && this.minecraft.player != null) {
            this.survivor.setDialogState(this.minecraft.player.getUUID(), next.id());
        }

        if (next.trustDelta() != 0) {
            spawnTrustPopup(
                    clickedButton.getX() + clickedButton.getWidth() / 2.0F,
                    clickedButton.getY(),
                    next.trustDelta()
            );
            this.survivor.addTrust(next.trustDelta());
        }

        showNpcReaction(next.id());
    }

    private void spawnTrustPopup(float x, float y, int delta) {
        trustPopups.add(new TrustPopup(x, y, delta));
    }

    private void renderTrustPopups(GuiGraphics guiGraphics) {
        long now = System.currentTimeMillis();

        for (int i = trustPopups.size() - 1; i >= 0; i--) {
            TrustPopup popup = trustPopups.get(i);
            float age = (now - popup.startTime) / (float) TRUST_POPUP_DURATION_MS;

            if (age >= 1.0F) {
                trustPopups.remove(i);
                continue;
            }

            float rise = 24.0F * (1.0F - (1.0F - age) * (1.0F - age));
            float wobble = (float) Math.sin(age * Math.PI * 3.0F) * 4.0F * (1.0F - age);
            float rotation = (float) Math.sin(age * Math.PI * 2.5F) * 7.0F * (1.0F - age);

            int alpha = Math.max(0, Math.min(255, (int) (255.0F * (1.0F - age * age))));
            int rgb = popup.delta > 0 ? 0x2E8B2E : 0xC42A2A;
            int color = (alpha << 24) | rgb;
            String popupText = (popup.delta > 0 ? "+" : "") + popup.delta;

            float textWidth = this.font.width(popupText);

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(
                    popup.startX + wobble,
                    popup.startY - rise - 2.0F,
                    0.0F
            );
            guiGraphics.pose().mulPose(
                    Axis.ZP.rotationDegrees(rotation)
            );

            guiGraphics.drawString(
                    this.font,
                    popupText,
                    (int) (-textWidth / 2.0F),
                    0,
                    color,
                    true
            );

            guiGraphics.pose().popPose();
        }
    }

    private void addBottomButton(int x, int y, int width, Component text, Button.OnPress action) {
        this.addRenderableWidget(new ParchmentButton(x, y, width, 16, text, action));
    }

    private void returnToParent() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parentScreen);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        DialogManager.NpcReaction npcReaction = DialogManager.getNpcReaction(this.currentNpcReactionId);
        if (npcReaction != null && npcReaction.options().size() > 1) {
            int count = npcReaction.options().size();
            if (delta > 0) {
                this.currentOptionIndex = (this.currentOptionIndex - 1 + count) % count;
                this.refreshButtons();
                return true;
            } else if (delta < 0) {
                this.currentOptionIndex = (this.currentOptionIndex + 1) % count;
                this.refreshButtons();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Klick überspringt den Typewriter und deckt sofort den gesamten Text auf
        if (this.visibleChars < this.activeFullText.length()) {
            this.visibleChars = this.activeFullText.length();
            return true;
        }

        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        // Pergament-Hintergrund
        guiGraphics.blit(
                PAPER_BG,
                this.leftPos,
                this.topPos,
                0,
                0,
                this.imageWidth,
                this.imageHeight,
                256,
                256
        );

        // Survivor-Portrait
        guiGraphics.blit(
                getPortraitLocation(),
                this.leftPos + 18,
                this.topPos + 16,
                0,
                0,
                32,
                32,
                32,
                32
        );

        // Survivor-Name
        guiGraphics.drawString(
                this.font,
                this.survivor.getSurvivorName(),
                this.leftPos + 56,
                this.topPos + 18,
                0x281608,
                false
        );

        // Vertrauens-Header statt generischem Subtitle
        int currentTrust = this.survivor.getTrust();
        Component relationText = SurvivorPersonality.getTrustRelationComponent(currentTrust);
        int relationColor = SurvivorPersonality.getTrustRelationColor(currentTrust);

        String sign = currentTrust > 0 ? "+" : "";
        Component statusDisplay = Component.literal("• ").append(relationText)
                .append(Component.literal(" (" + sign + currentTrust + ")"));

        guiGraphics.drawString(
                this.font,
                statusDisplay,
                this.leftPos + 56,
                this.topPos + 30,
                relationColor,
                false
        );

        // Typewriter-Ticker & Villager-Audio
        long currentTime = System.currentTimeMillis();
        if (visibleChars < activeFullText.length() && currentTime - lastTickTime > 28) {
            visibleChars++;
            lastTickTime = currentTime;

            if (this.minecraft != null && this.minecraft.player != null) {
                float pitch = this.survivor.isFemale() ? 1.6F : 0.9F;
                pitch += (float) (Math.random() * 0.16F - 0.08F);
                this.minecraft.getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.VILLAGER_AMBIENT, pitch, 0.12F)
                );
            }
        }

        String textToDraw = activeFullText.substring(0, Math.min(visibleChars, activeFullText.length()));

        guiGraphics.drawWordWrap(
                this.font,
                Component.literal("\"" + textToDraw + "\""),
                this.leftPos + 18,
                this.topPos + 52,
                154,
                0x2E1C10
        );

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTrustPopups(guiGraphics);
    }

    private ResourceLocation getPortraitLocation() {
        String gender = this.survivor.isFemale() ? "female" : "male";
        String suffix = this.survivor.getTextureSuffix();
        return new ResourceLocation(
                "minecolonies",
                "textures/entity_icon/citizen/default/knight"
                        + gender
                        + "1"
                        + suffix
                        + ".png"
        );
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}