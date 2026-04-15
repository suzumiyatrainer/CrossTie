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
 * CrossTie - RTM / KaizPatchX 向けの描画・更新最適化 Mod。
 *
 * TPS 側の更新制御と FPS 側の描画制御を両方扱い、UniMixins の Late Mixin を使って
 * バイナリ Mod に差し込みます。
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
        LOGGER.info("CrossTie 初期化前処理");

        // Mod 検出システムを初期化する
        ModDetector.detectMods();
        ModDetector.logDetectedMods();

        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        LOGGER.info("CrossTie 初期化");
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        LOGGER.info("CrossTie 初期化後処理");
        proxy.postInit(event);
    }

    @cpw.mods.fml.common.network.NetworkCheckHandler
    public boolean checkModLists(java.util.Map<String, String> modList, cpw.mods.fml.relauncher.Side side) {
        return true;
    }
}
