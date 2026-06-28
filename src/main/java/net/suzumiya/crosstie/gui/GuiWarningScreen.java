package net.suzumiya.crosstie.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

public class GuiWarningScreen extends GuiScreen {
    private final GuiScreen parentScreen;
    private final String warningMessage;

    public GuiWarningScreen(GuiScreen parentScreen, String warningMessage) {
        this.parentScreen = parentScreen;
        this.warningMessage = warningMessage;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void initGui() {
        this.buttonList.clear();
        this.buttonList.add(new GuiButton(0, this.width / 2 - 100, this.height / 2 + 30, I18n.format("gui.back")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            this.mc.displayGuiScreen(this.parentScreen);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.drawCenteredString(this.fontRendererObj, this.warningMessage, this.width / 2, this.height / 2 - 10, 0xFF5555);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
