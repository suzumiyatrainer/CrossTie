package net.suzumiya.crosstie.gui;

import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.GuiConfigEntries;
import cpw.mods.fml.client.config.IConfigElement;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.GuiYesNoCallback;
import net.minecraft.client.resources.I18n;

@SuppressWarnings({ "rawtypes" })
public class ActionReloadEntry extends GuiConfigEntries.ButtonEntry implements GuiYesNoCallback {

    public ActionReloadEntry(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement configElement) {
        super(owningScreen, owningEntryList, configElement);
        this.btnValue.displayString = I18n.format(configElement.getName());
    }

    @Override
    public boolean mousePressed(int slotIndex, int x, int y, int mouseEvent, int relativeX, int relativeY) {
        if (this.btnValue.mousePressed(this.mc, x, y)) {
            this.btnValue.func_146113_a(this.mc.getSoundHandler());

            if (this.mc.theWorld != null) {
                this.mc.displayGuiScreen(
                        new GuiWarningScreen(this.owningScreen, I18n.format("crosstie.gui.reloadPacks.warn_in_world")));
            } else {
                GuiYesNo guiYesNo = new GuiYesNo(this, I18n.format("crosstie.gui.reloadPacks.confirm"), "", 0);
                this.mc.displayGuiScreen(guiYesNo);
            }
            return true;
        }
        return false;
    }

    @Override
    public void confirmClicked(boolean result, int id) {
        if (result) {
            Thread loadingThread = RTMReloadPacksLogic.reloadPacks();
            GuiReloadingWaitScreen waitScreen = new GuiReloadingWaitScreen(loadingThread);
            if (loadingThread == null) {
                waitScreen.hasError = true;
            } else {
                loadingThread.setUncaughtExceptionHandler((t, e) -> {
                    waitScreen.hasError = true;
                    net.suzumiya.crosstie.CrossTie.LOGGER.error("Background loading thread crashed", e);
                });
            }
            this.mc.displayGuiScreen(waitScreen);
        } else {
            this.mc.displayGuiScreen(this.owningScreen);
        }
    }

    @Override
    public Object[] getCurrentValues() {
        return new Object[0];
    }

    public Object getCurrentValue() {
        return null;
    }

    @Override
    public boolean saveConfigElement() {
        return false;
    }

    @Override
    public boolean isDefault() {
        return true;
    }

    @Override
    public void setToDefault() {
    }

    @Override
    public boolean isChanged() {
        return false;
    }

    @Override
    public void undoChanges() {
    }

    @Override
    public void updateValueButtonText() {
        this.btnValue.displayString = I18n.format(this.configElement.getName());
    }

    @Override
    public void valueButtonPressed(int slotIndex) {
    }
}
