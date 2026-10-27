package net.suzumiya.crosstie;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = Tags.MODID, name = Tags.MODNAME, version = Tags.VERSION, dependencies = "required-after:unimixins", guiFactory = "net.suzumiya.crosstie.gui.CrossTieGuiFactory")
public class CrossTie {

    public static final Logger LOGGER = LogManager.getLogger("CrossTie");

    @Mod.Instance(Tags.MODID)
    public static CrossTie instance;

    /**
     * Pre-Initialization イベントハンドラ。
     *
     * <p>
     * Forge の {@link cpw.mods.fml.common.config.Configuration Configuration} を使い
     * {@code config/crosstie.cfg} を自動生成する。 初回起動時はデフォルト値でファイルが作成される。
     *
     * @param event FMLPreInitializationEvent
     */
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Configuration を初期化し config/crosstie.cfg を生成/ロード
        CrossTieConfig.init(event);

        // ネットワーク初期化
        net.suzumiya.crosstie.network.CrossTiePacketHandler.init();

        // システムプロパティによるオーバーライドを適用
        CrossTieConfig.applySystemPropertyOverrides();

        // RTM列車用最小限チャンクローダーコールバックおよびイベント登録
        net.minecraftforge.common.ForgeChunkManager.setForcedChunkLoadingCallback(this,
                net.suzumiya.crosstie.world.TrainChunkLoaderManager.INSTANCE);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS
                .register(net.suzumiya.crosstie.world.TrainChunkLoaderManager.INSTANCE);

        // サーバー側メモリ最適化マネージャー初期化
        net.suzumiya.crosstie.server.ServerMemoryManager.INSTANCE.init();

        LOGGER.info("CrossTie preInit completed. Config file: {}",
                event.getSuggestedConfigurationFile().getAbsolutePath());
    }

    @Mod.EventHandler
    public void init(cpw.mods.fml.common.event.FMLInitializationEvent event) {
        if (event.getSide() == cpw.mods.fml.relauncher.Side.CLIENT) {
            net.suzumiya.crosstie.client.CrossTieKeyBindings.init();
            net.minecraftforge.client.ClientCommandHandler.instance
                    .registerCommand(new net.suzumiya.crosstie.client.gui.CommandViewImage());
            net.minecraftforge.common.MinecraftForge.EVENT_BUS
                    .register(new net.suzumiya.crosstie.gui.CrossTieGuiEventHandler());
            net.minecraftforge.common.MinecraftForge.EVENT_BUS
                    .register(new net.suzumiya.crosstie.client.WireFastRemoveTracker());
            net.minecraftforge.common.MinecraftForge.EVENT_BUS
                    .register(new net.suzumiya.crosstie.client.sound.SoundPreloadManager.SoundPreloadEventHandler());
            cpw.mods.fml.common.FMLCommonHandler.instance().bus()
                    .register(new net.suzumiya.crosstie.client.CrossTieDiagnosticsTicker());
            // lwjgl3ify(SDL3)環境のIME汎用補正
            try {
                Class.forName("org.lwjglx.opengl.Display");
                cpw.mods.fml.common.FMLCommonHandler.instance().bus()
                        .register(new net.suzumiya.crosstie.client.ImeTicker());
                LOGGER.info("lwjgl3ify detected: IME compat enabled");
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Server Starting イベントハンドラ。
     *
     * <p>
     * {@code /crosstie} コマンドをサーバーに登録する。
     *
     * @param event FMLServerStartingEvent
     */
    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new net.suzumiya.crosstie.command.CommandCrossTie());
        event.registerServerCommand(new net.suzumiya.crosstie.command.CommandViewImageFallback());
        LOGGER.info("Registered /crosstie and /crosstieview commands");

        // Discord 連携初期化 (Dedicated Server かつ enabled=true 時のみ動作)
        net.suzumiya.crosstie.discord.DiscordManager.onServerStarting(event);
    }

    @Mod.EventHandler
    public void serverStopping(cpw.mods.fml.common.event.FMLServerStoppingEvent event) {
        net.suzumiya.crosstie.world.TrainChunkLoaderManager.INSTANCE.clearAll();
        net.suzumiya.crosstie.server.ServerMemoryManager.INSTANCE.onServerStopping();
        net.suzumiya.crosstie.discord.DiscordManager.onServerStopping(event);
    }

    @cpw.mods.fml.common.network.NetworkCheckHandler
    public boolean checkModLists(java.util.Map<String, String> modList, cpw.mods.fml.relauncher.Side side) {
        return true;
    }
}
