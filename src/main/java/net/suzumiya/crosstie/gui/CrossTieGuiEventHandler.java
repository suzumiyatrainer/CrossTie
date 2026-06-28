package net.suzumiya.crosstie.gui;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptions;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.suzumiya.crosstie.Tags;

public class CrossTieGuiEventHandler {

    private static final int BUTTON_ID_CROSSTIE = 133701;

    @SuppressWarnings("unchecked")
    @SubscribeEvent
    public void onInitGuiPost(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.gui instanceof GuiOptions) {
            int x = event.gui.width / 2 + 5;
            int y = event.gui.height / 6 + 48 - 6;
            event.buttonList.add(new GuiButton(BUTTON_ID_CROSSTIE, x, y, 150, 20, Tags.MODNAME));
        }
    }

    @SubscribeEvent
    public void onActionPerformed(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (event.gui instanceof GuiOptions && event.button.id == BUTTON_ID_CROSSTIE) {
            Minecraft mc = Minecraft.getMinecraft();
            mc.displayGuiScreen(new CrossTieConfigGui(event.gui));
            event.setCanceled(true);
        }
    }
}
