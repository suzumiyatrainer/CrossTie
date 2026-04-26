package net.suzumiya.crosstie;

import cpw.mods.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = Tags.MODID, name = Tags.MODNAME, version = Tags.VERSION, dependencies = "required-after:unimixins")
public class CrossTie {

    public static final Logger LOGGER = LogManager.getLogger(Tags.MODID);

    @Mod.Instance(Tags.MODID)
    public static CrossTie instance;

    @cpw.mods.fml.common.network.NetworkCheckHandler
    public boolean checkModLists(java.util.Map<String, String> modList, cpw.mods.fml.relauncher.Side side) {
        return true;
    }
}
