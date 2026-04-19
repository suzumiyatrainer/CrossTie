package net.suzumiya.crosstie.config;

import cpw.mods.fml.common.FMLLog;
import net.minecraftforge.common.config.Configuration;

import java.io.File;

public class CrossTieConfig {
    public static Configuration config;

    // FPS
    public static boolean enableRenderCulling = true;
    public static boolean fixAngelicaRailCulling = false;
    public static boolean enableAngelicaFastPath = true;
    public static boolean enableAngelicaIfTTTCache = true;
    public static boolean enableAngelicaFallbackGuard = true;

    // TPS
    public static boolean enableTileEntityUpdates = true;

    public static void init(File configFile) {
        config = new Configuration(configFile);
        syncConfig();
    }

    public static void syncConfig() {
        try {
            config.load();

            enableRenderCulling = config.getBoolean("enableRenderCulling", "fps", true,
                    "Enable render culling for RTM entities and tiles.");

            fixAngelicaRailCulling = config.getBoolean("fixAngelicaRailCulling", "fps", false,
                    "Expand rail bounding boxes to mitigate Angelica/Sodium rail culling issues.");

            enableAngelicaFastPath = config.getBoolean("enableAngelicaFastPath", "fps", true,
                    "Use Angelica fast-path state cache and direct-buffer staging for corrected render data.");

            enableAngelicaIfTTTCache = config.getBoolean("enableAngelicaIfTTTCache", "fps", true,
                    "Consume ATSAssist IFTTT post-update snapshots as render-only immutable cache.");

            enableAngelicaFallbackGuard = config.getBoolean("enableAngelicaFallbackGuard", "fps", true,
                    "Temporarily disable Angelica optimizations and fallback when render anomalies are detected.");

            enableTileEntityUpdates = config.getBoolean("enableTileEntityUpdates", "tps", true,
                    "Enable tile entity update optimization.");

        } catch (Exception e) {
            FMLLog.getLogger().error("[CrossTie] Failed to load configuration", e);
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }
}
