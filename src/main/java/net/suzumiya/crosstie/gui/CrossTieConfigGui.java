package net.suzumiya.crosstie.gui;

import cpw.mods.fml.client.config.DummyConfigElement;
import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;
import net.minecraft.client.gui.GuiScreen;
import net.suzumiya.crosstie.Tags;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"rawtypes", "unchecked"})
public class CrossTieConfigGui extends GuiConfig {

    public CrossTieConfigGui(GuiScreen parent) {
        super(parent, getConfigElements(), Tags.MODID, false, false, Tags.MODNAME + " Configuration");
    }

    private static List<IConfigElement> getConfigElements() {
        List<IConfigElement> elements = new ArrayList<>();
        
        // Custom category for RTM
        List<IConfigElement> rtmElements = new ArrayList<>();
        rtmElements.add(new DummyConfigElement.DummyCategoryElement(
                "reloadPacks", 
                "crosstie.config.rtm.reloadPacks.name", 
                ActionReloadEntry.class
        ));
        
        elements.add(new DummyConfigElement.DummyCategoryElement(
                "rtm", 
                "crosstie.config.rtm.name", 
                rtmElements
        ));

        return elements;
    }
}
