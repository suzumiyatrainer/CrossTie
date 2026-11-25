package net.suzumiya.crosstie.gui;

import cpw.mods.fml.client.config.DummyConfigElement;
import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.suzumiya.crosstie.CrossTieConfig;
import net.suzumiya.crosstie.Tags;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"rawtypes", "unchecked"})
public class CrossTieConfigGui extends GuiConfig {

    private final boolean initialGlassCtm;

    public CrossTieConfigGui(GuiScreen parent) {
        super(parent, getConfigElements(), Tags.MODID, false, false, Tags.MODNAME + " Configuration");
        this.initialGlassCtm = CrossTieConfig.enableGlassConnectedTextures;
    }

    private static List<IConfigElement> getConfigElements() {
        List<IConfigElement> elements = new ArrayList<>();

        if (CrossTieConfig.getConfig() != null) {
            elements.add(new ConfigElement(CrossTieConfig.getConfig().getCategory("features")));
            elements.add(new ConfigElement(CrossTieConfig.getConfig().getCategory("performance")));
            elements.add(new ConfigElement(CrossTieConfig.getConfig().getCategory("fixes")));
        }

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

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        CrossTieConfig.saveAndSync();

        if (this.initialGlassCtm != CrossTieConfig.enableGlassConnectedTextures) {
            try {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc != null) {
                    mc.refreshResources();
                }
            } catch (Throwable t) {
                // Ignore if called before fully initialized
            }
        }
    }
}
