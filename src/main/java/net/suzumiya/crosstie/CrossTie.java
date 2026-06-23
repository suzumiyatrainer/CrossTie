package net.suzumiya.crosstie;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = Tags.MODID, name = Tags.MODNAME, version = Tags.VERSION, dependencies = "required-after:unimixins")
public class CrossTie {

    public static final Logger LOGGER = LogManager.getLogger(Tags.MODID);

    @Mod.Instance(Tags.MODID)
    public static CrossTie instance;

    /**
     * Pre-Initialization イベントハンドラ。
     *
     * <p>Forge の {@link cpw.mods.fml.common.config.Configuration Configuration} を使い
     * {@code config/crosstie.cfg} を自動生成する。
     * 初回起動時はデフォルト値でファイルが作成される。
     *
     * @param event FMLPreInitializationEvent
     */
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Configuration を初期化し config/crosstie.cfg を生成/ロード
        CrossTieConfig.init(event);

        // システムプロパティによるオーバーライドを適用
        CrossTieConfig.applySystemPropertyOverrides();

        LOGGER.info("CrossTie preInit completed. Config file: {}",
                event.getSuggestedConfigurationFile().getAbsolutePath());
    }

    /**
     * Server Starting イベントハンドラ。
     *
     * <p>{@code /crosstie} コマンドをサーバーに登録する。
     *
     * @param event FMLServerStartingEvent
     */
    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new net.suzumiya.crosstie.command.CommandCrossTie());
        LOGGER.info("Registered /crosstie command");
    }

    @cpw.mods.fml.common.network.NetworkCheckHandler
    public boolean checkModLists(java.util.Map<String, String> modList, cpw.mods.fml.relauncher.Side side) {
        return true;
    }
}
