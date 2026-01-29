package net.suzumiya.crosstie.config;

import net.minecraftforge.common.config.Configuration;
import cpw.mods.fml.common.FMLLog;
import java.io.File;

public class CrossTieConfig {
    public static Configuration config;

    // FPS Optimizations
    public static boolean enableRenderCulling = true;
    public static boolean fixAngelicaRailCulling = false; // Default false as per request

    // TPS Optimizations
    public static boolean enableTileEntityUpdates = true;

    public static void init(File configFile) {
        config = new Configuration(configFile);
        syncConfig();
    }

    public static void syncConfig() {
        try {
            config.load();

            // FPS Category
            enableRenderCulling = config.getBoolean("enableRenderCulling", "fps", true,
                    "Enable render culling for RTM vehicles and machines.");

            fixAngelicaRailCulling = config.getBoolean("fixAngelicaRailCulling", "fps", false,
                    "Fixes rail culling issues with Angelica/Sodium by extending the bounding box of rails. May reduce FPS slightly when enabled.");

            // TPS Category
            enableTileEntityUpdates = config.getBoolean("enableTileEntityUpdates", "tps", true,
                    "Enable server-side update optimizations for TileEntities.");

        } catch (Exception e) {
            FMLLog.getLogger().error("[CrossTie] Failed to load configuration", e);
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }
}
