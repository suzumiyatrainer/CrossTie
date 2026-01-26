package net.suzumiya.crosstie.config;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import java.io.File;

/**
 * CrossTie Configuration Handler
 * Mixinロード時にも使用可能なように設計
 */
public class CrossTieConfig {

    public static Configuration config;

    // General Settings
    public static boolean enableRTM = true;
    public static boolean enableBamboo = true;
    public static boolean enableOEMod = true;

    // Optimization Settings
    public static int renderDistanceOffset = 2; // Default: RenderDistance + 2

    // RTM Specific
    public static boolean enableRTMCulling = true;

    // File Path (Mixin Pluginから設定)
    public static File configFile;

    public static void init(File file) {
        configFile = file;
        config = new Configuration(configFile);
        syncConfig();
    }

    public static void syncConfig() {
        if (config == null) {
            if (configFile != null)
                config = new Configuration(configFile);
            else
                return;
        }

        try {
            config.load();

            // Modules (TEMPORARY STUB for Build Check)
            // Property code commented out to verify build classpath
            /*
             * // Modules
             * Property pRTM = config.get("modules", "EnableRTM", true);
             * pRTM.comment = "Enable RTM (RealTrainMod) Optimizations";
             * enableRTM = pRTM.getBoolean();
             * 
             * Property pBamboo = config.get("modules", "EnableBamboo", true);
             * pBamboo.comment = "Enable BambooMod Optimizations";
             * enableBamboo = pBamboo.getBoolean();
             * 
             * Property pOE = config.get("modules", "EnableOEMod", true);
             * pOE.comment = "Enable OEMod Optimizations";
             * enableOEMod = pOE.getBoolean();
             * 
             * // Optimizations
             * Property pCull = config.get("optimizations", "RTMCulling", true);
             * pCull.comment =
             * "Enable Render Distance Culling for RTM Entities (Trains, Floor)";
             * enableRTMCulling = pCull.getBoolean();
             * 
             * Property pDist = config.get("optimizations", "RenderDistanceOffset", 2);
             * pDist.comment =
             * "Chunk offset for render distance culling (RenderDistance + Offset) [0-16]";
             * renderDistanceOffset = pDist.getInt();
             */

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (config.hasChanged())
                config.save();
        }
    }
}
