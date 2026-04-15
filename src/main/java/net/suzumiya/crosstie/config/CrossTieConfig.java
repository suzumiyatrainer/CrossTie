package net.suzumiya.crosstie.config;

import net.minecraftforge.common.config.Configuration;
import cpw.mods.fml.common.FMLLog;
import java.io.File;

public class CrossTieConfig {
    public static Configuration config;

    // FPS 最適化
    public static boolean enableRenderCulling = true;
    public static boolean fixAngelicaRailCulling = false; // 既定値は false

    // TPS 最適化
    public static boolean enableTileEntityUpdates = true;

    public static void init(File configFile) {
        config = new Configuration(configFile);
        syncConfig();
    }

    public static void syncConfig() {
        try {
            config.load();

            // FPS 設定項目
            enableRenderCulling = config.getBoolean("enableRenderCulling", "fps", true,
                    "RTM の車両と機械に対する描画カリングを有効にします。");

            fixAngelicaRailCulling = config.getBoolean("fixAngelicaRailCulling", "fps", false,
                    "レールのバウンディングボックスを広げて、Angelica / Sodium で起きるレールカリング問題を修正します。"
                            + "有効にすると FPS が少し下がる場合があります。");

            // TPS 設定項目
            enableTileEntityUpdates = config.getBoolean("enableTileEntityUpdates", "tps", true,
                    "TileEntity のサーバー側更新最適化を有効にします。");

        } catch (Exception e) {
            FMLLog.getLogger().error("[CrossTie] Failed to load configuration", e);
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }
}
