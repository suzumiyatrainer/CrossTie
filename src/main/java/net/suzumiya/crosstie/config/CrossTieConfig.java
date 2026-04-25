package net.suzumiya.crosstie.config;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Loader;
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
    public static boolean enableHi03LegacyAngelicaDisplayLists = true;
    public static boolean enableHi03LegacyAngelicaBypass = false;

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

            enableHi03LegacyAngelicaDisplayLists = config.getBoolean("enableHi03LegacyAngelicaDisplayLists", "fps",
                    Loader.isModLoaded("angelica"),
                    "Use native legacy display lists for hi03 rail script caches under Angelica. This improves hi03 rail performance without re-enabling the full raw-OpenGL bypass.");

            enableHi03LegacyAngelicaBypass = config.getBoolean("enableHi03LegacyAngelicaBypass", "fps",
                    !Loader.isModLoaded("angelica"),
                    "Keep CrossTie's legacy OpenGL hi03 rail bypass active under Angelica. Disable this when Angelica freezes or crashes around hi03 rail rendering.");

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
