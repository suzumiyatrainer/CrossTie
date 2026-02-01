package net.suzumiya.crosstie;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import net.suzumiya.crosstie.util.ModDetector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * CrossTie - RTM/KaizPatchX及び関連Mod向け最適化Mod
 * 
 * TPS（サーバー処理）とFPS（クライアント描画）の両面から最適化を提供します。
 * UniMixinsベースのLate Mixinを使用してバイナリModに介入します。
 */
@Mod(modid = Tags.MODID, name = Tags.MODNAME, version = Tags.VERSION, dependencies = "required-after:unimixins")
public class CrossTie {

    public static final Logger LOGGER = LogManager.getLogger(Tags.MODID);

    @Mod.Instance(Tags.MODID)
    public static CrossTie instance;

    @SidedProxy(clientSide = "net.suzumiya.crosstie.ClientProxy", serverSide = "net.suzumiya.crosstie.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("CrossTie Pre-Initialization");

        // Mod検出システム初期化
        ModDetector.detectMods();
        ModDetector.logDetectedMods();

        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        LOGGER.info("CrossTie Initialization");
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        LOGGER.info("CrossTie Post-Initialization");
        proxy.postInit(event);
    }

    @cpw.mods.fml.common.network.NetworkCheckHandler
    public boolean checkModLists(java.util.Map<String, String> modList, cpw.mods.fml.relauncher.Side side) {
        return true;
    }
}
