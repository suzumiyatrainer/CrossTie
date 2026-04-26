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
    public static boolean enableScriptRenderFunctionCache = true;

    // TPS
    public static boolean enableTileEntityUpdates = true;
    public static boolean enableTrainSpatialTracker = true;
    public static boolean enableSignalReflectionCache = true;
    public static boolean enableWebCTCReflectionCache = true;
    public static boolean enableWebCTCChunkLoadRateLimit = true;
    public static int webCTCChunkLoadsPerTick = 2;

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

            enableScriptRenderFunctionCache = config.getBoolean("enableScriptRenderFunctionCache", "fps", true,
                    "Cache RTM/Nashorn Invocable script render lookups while preserving the original script API.");

            enableTileEntityUpdates = config.getBoolean("enableTileEntityUpdates", "tps", true,
                    "Enable tile entity update optimization.");

            enableTrainSpatialTracker = config.getBoolean("enableTrainSpatialTracker", "tps", true,
                    "Maintain a server-side train spatial index used by ATSAssist IFTTT detection Mixins.");

            enableSignalReflectionCache = config.getBoolean("enableSignalReflectionCache", "tps", true,
                    "Cache RTM TileEntitySignal signalLevel reflection for SignalControllerMod.");

            enableWebCTCReflectionCache = config.getBoolean("enableWebCTCReflectionCache", "tps", true,
                    "Cache RTM TileEntitySignal reflection used by WebCTC rail group state calculation.");

            enableWebCTCChunkLoadRateLimit = config.getBoolean("enableWebCTCChunkLoadRateLimit", "tps", true,
                    "Rate-limit WebCTC's forced rail chunk loads to reduce server tick spikes.");

            webCTCChunkLoadsPerTick = config.getInt("webCTCChunkLoadsPerTick", "tps", 2, 0, 64,
                    "Maximum WebCTC forced rail chunk loads allowed per server tick. 0 disables forced loads.");

        } catch (Exception e) {
            FMLLog.getLogger().error("[CrossTie] Failed to load configuration", e);
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }
}
