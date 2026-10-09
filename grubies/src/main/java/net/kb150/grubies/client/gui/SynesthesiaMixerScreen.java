package net.kb150.grubies.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.kb150.grubies.client.ClientTripHandler;
import net.kb150.grubies.client.synesthesia.SynesthesiaState;
import net.kb150.grubies.client.vfx.ClientShaderManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class SynesthesiaMixerScreen extends Screen {
    private float scrollX = 0;
    private int selectedInJack = -1, selectedOutJack = -1;
    private boolean draggingSlider = false;
    private int dragTargetIndex = -1;
    private boolean dragIsInput = false;

    public SynesthesiaMixerScreen() {
        super(Component.literal("Synesthesia Mixer"));
        ClientTripHandler.manualPatchMode = true;
        ClientShaderManager.setPipeline("grub_master");
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(g);
        int startX = 20 - (int) scrollX;
        String hoveredTooltip = null;

        int outCount = ClientTripHandler.getOutputCount();

        // EINGÄNGE
        for (int i = 0; i < ClientTripHandler.INPUT_COUNT; i++) {
            int x = startX + i * 60;
            drawModule(g, x, 30, "IN " + i, ClientTripHandler.inputGains[i]);
            drawJack(g, x + 20, 110, selectedInJack == i ? 0xFF00FF00 : 0xFF808080);

            if (mouseX >= x && mouseX <= x + 40 && mouseY >= 30 && mouseY <= 115) {
                String name = (i < SynesthesiaState.INPUT_NAMES.length) ? SynesthesiaState.INPUT_NAMES[i] : "Input " + i;
                hoveredTooltip = "IN [" + i + "]: " + name + " | Gain: " + String.format("%.2f", ClientTripHandler.inputGains[i]);
            }
        }

        // AUSGÄNGE (Dynamisch über CHANNELS.size())
        for (int j = 0; j < outCount; j++) {
            int x = startX + j * 60;
            drawJack(g, x + 20, 140, selectedOutJack == j ? 0xFF00FF00 : 0xFF808080);
            drawModule(g, x, 155, "OUT " + j, ClientTripHandler.outputGains[j]);

            if (mouseX >= x && mouseX <= x + 40 && mouseY >= 135 && mouseY <= 230) {
                String name = ClientTripHandler.getOutputName(j);
                hoveredTooltip = "OUT [" + j + "]: " + name + " | Gain: " + String.format("%.2f", ClientTripHandler.outputGains[j]);
            }
        }

        // PATCH-KABEL
        for (int i = 0; i < ClientTripHandler.INPUT_COUNT; i++) {
            for (int j = 0; j < outCount; j++) {
                if (ClientTripHandler.patchConnections[i][j]) {
                    drawLine(startX + i * 60 + 20, 110, startX + j * 60 + 20, 140, 0xFFFFCC00);
                }
            }
        }

        // PROJEKTIONSKABEL
        if (selectedInJack != -1) {
            drawLine(startX + selectedInJack * 60 + 20, 110, mouseX, mouseY, 0xFF00FF00);
        } else if (selectedOutJack != -1) {
            drawLine(mouseX, mouseY, startX + selectedOutJack * 60 + 20, 140, 0xFF00FF00);
        }

        g.drawString(this.font, "Mausrad: Scrollen | L-Klick Buchse: Verbinden | R-Klick Buchse: Kabel Löschen", 10, 10, 0xFFFFFFFF);

        if (hoveredTooltip != null) {
            g.renderTooltip(this.font, Component.literal(hoveredTooltip), mouseX, mouseY);
        }

        super.render(g, mouseX, mouseY, partialTicks);
    }

    private void drawModule(GuiGraphics g, int x, int y, String label, float val) {
        g.fill(x, y, x + 40, y + 75, 0xFF222222);
        g.drawString(this.font, label, x + 4, y + 4, 0xFFAAAAAA);
        g.fill(x + 18, y + 18, x + 22, y + 68, 0xFF444444);
        int handleY = y + 43 - (int) (val * 23.0f);
        g.fill(x + 12, handleY - 3, x + 28, handleY + 3, 0xFF00AAAA);
    }

    private void drawJack(GuiGraphics g, int x, int y, int color) {
        g.fill(x - 5, y - 5, x + 5, y + 5, color);
        g.fill(x - 3, y - 3, x + 3, y + 3, 0xFF000000);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int startX = 20 - (int) scrollX;
        int outCount = ClientTripHandler.getOutputCount();

        if (button == 1) {
            selectedInJack = selectedOutJack = -1;
            checkAndRemoveCableAt(mouseX, mouseY, startX);
            return true;
        }

        if (button == 0) {
            for (int i = 0; i < ClientTripHandler.INPUT_COUNT; i++) {
                if (Math.hypot(mouseX - (startX + i * 60 + 20), mouseY - 110) < 8) {
                    if (selectedOutJack != -1) {
                        ClientTripHandler.patchConnections[i][selectedOutJack] = true;
                        selectedOutJack = -1;
                    } else selectedInJack = i;
                    return true;
                }
            }

            for (int j = 0; j < outCount; j++) {
                if (Math.hypot(mouseX - (startX + j * 60 + 20), mouseY - 140) < 8) {
                    if (selectedInJack != -1) {
                        ClientTripHandler.patchConnections[selectedInJack][j] = true;
                        selectedInJack = -1;
                    } else selectedOutJack = j;
                    return true;
                }
            }

            checkSliderClick(mouseX, mouseY, startX);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void checkSliderClick(double mX, double mY, int startX) {
        int outCount = ClientTripHandler.getOutputCount();
        for (int i = 0; i < ClientTripHandler.INPUT_COUNT; i++) {
            int x = startX + i * 60;
            if (mX >= x + 10 && mX <= x + 30 && mY >= 48 && mY <= 98) {
                draggingSlider = true; dragIsInput = true; dragTargetIndex = i;
                updateSliderValue(mY, 48);
                return;
            }
        }
        for (int j = 0; j < outCount; j++) {
            int x = startX + j * 60;
            if (mX >= x + 10 && mX <= x + 30 && mY >= 173 && mY <= 223) {
                draggingSlider = true; dragIsInput = false; dragTargetIndex = j;
                updateSliderValue(mY, 173);
                return;
            }
        }
    }

    @Override
    public boolean mouseDragged(double mX, double mY, int button, double dX, double dY) {
        if (draggingSlider && dragTargetIndex != -1) {
            updateSliderValue(mY, dragIsInput ? 48 : 173);
            return true;
        }
        return super.mouseDragged(mX, mY, button, dX, dY);
    }

    @Override
    public boolean mouseReleased(double mX, double mY, int button) {
        draggingSlider = false; dragTargetIndex = -1;
        return super.mouseReleased(mX, mY, button);
    }

    private void updateSliderValue(double mY, int topY) {
        float norm = (float) (1.0 - ((mY - topY) / 25.0));
        float val = Mth.clamp(norm, -1.0f, 1.0f);
        if (dragIsInput) ClientTripHandler.inputGains[dragTargetIndex] = val;
        else ClientTripHandler.outputGains[dragTargetIndex] = val;
    }

    private void checkAndRemoveCableAt(double mX, double mY, int startX) {
        int outCount = ClientTripHandler.getOutputCount();
        for (int i = 0; i < ClientTripHandler.INPUT_COUNT; i++) {
            if (Math.hypot(mX - (startX + i * 60 + 20), mY - 110) < 8) {
                for (int j = 0; j < outCount; j++) ClientTripHandler.patchConnections[i][j] = false;
            }
        }
        for (int j = 0; j < outCount; j++) {
            if (Math.hypot(mX - (startX + j * 60 + 20), mY - 140) < 8) {
                for (int i = 0; i < ClientTripHandler.INPUT_COUNT; i++) ClientTripHandler.patchConnections[i][j] = false;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxModules = Math.max(ClientTripHandler.INPUT_COUNT, ClientTripHandler.getOutputCount());
        scrollX = Mth.clamp(scrollX - (float) delta * 25.0f, 0f, Math.max(0f, maxModules * 60f - this.width + 40f));
        return true;
    }

    private void drawLine(int x1, int y1, int x2, int y2, int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        
        float a = (color >> 24 & 255) / 255.0f, r = (color >> 16 & 255) / 255.0f;
        float g = (color >> 8 & 255) / 255.0f, b = (color & 255) / 255.0f;
        
        buffer.vertex(x1, y1, 0).color(r, g, b, a).endVertex();
        buffer.vertex(x2, y2, 0).color(r, g, b, a).endVertex();
        
        tesselator.end();
        RenderSystem.disableBlend();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}