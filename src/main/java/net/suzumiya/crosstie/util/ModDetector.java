package net.suzumiya.crosstie.util;

import cpw.mods.fml.common.Loader;
import net.suzumiya.crosstie.CrossTie;

/**
 * 実行時のMod検出システム
 * 
 * 存在しないModの最適化を自動的に無効化するために使用します。
 */
public class ModDetector {

    // ターゲットMod
    public static boolean hasRTM = false;
    public static boolean hasBamboo = false;
    public static boolean hasOEMod = false;
    public static boolean hasATSAssist = false;
    public static boolean hasSignalController = false;

    // 軽量化Mod (互換性確認用)
    public static boolean hasOptiFine = false;
    public static boolean hasAngelica = false;
    public static boolean hasFalseTweaks = false;
    public static boolean hasBeddium = false;
    public static boolean hasSwanSong = false;
    public static boolean hasFastCraft = false;
    public static boolean hasCoreTweaks = false;

    /**
     * 全ての関連Modの存在を検出
     */
    public static void detectMods() {
        // ターゲットMod検出
        hasRTM = Loader.isModLoaded("RTM");
        hasBamboo = Loader.isModLoaded("Bamboo");
        hasOEMod = Loader.isModLoaded("OEMod");
        hasATSAssist = Loader.isModLoaded("ATSAssist");
        hasSignalController = Loader.isModLoaded("SignalController");

        // 軽量化Mod検出
        hasAngelica = Loader.isModLoaded("angelica");
        hasFalseTweaks = Loader.isModLoaded("falsetweaks");
        hasBeddium = Loader.isModLoaded("beddium");
        hasSwanSong = Loader.isModLoaded("swansong");
        hasCoreTweaks = Loader.isModLoaded("coretweaks");

        // OptiFineはリフレクションで検出（ModIDを持たないため）
        hasOptiFine = checkOptiFine();

        // FastCraftもリフレクションで検出
        hasFastCraft = checkFastCraft();
    }

    /**
     * OptiFineの存在をリフレクションで確認
     */
    private static boolean checkOptiFine() {
        try {
            Class.forName("optifine.OptiFineForgeTweaker");
            return true;
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("Config");
                return true;
            } catch (ClassNotFoundException e2) {
                return false;
            }
        }
    }

    /**
     * FastCraftの存在をリフレクションで確認
     */
    private static boolean checkFastCraft() {
        try {
            Class.forName("fastcraft.Tweaker");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 検出したModをログに出力
     */
    public static void logDetectedMods() {
        CrossTie.LOGGER.info("=== CrossTie Mod Detection ===");

        CrossTie.LOGGER.info("Target Mods:");
        logMod("RTM/KaizPatchX", hasRTM);
        logMod("Bamboo", hasBamboo);
        logMod("OEMod", hasOEMod);
        logMod("ATSAssist", hasATSAssist);
        logMod("SignalController", hasSignalController);

        CrossTie.LOGGER.info("Performance Mods (Compatibility Layer):");
        logMod("OptiFine", hasOptiFine);
        logMod("Angelica", hasAngelica);
        logMod("FalseTweaks", hasFalseTweaks);
        logMod("Beddium", hasBeddium);
        logMod("SwanSong", hasSwanSong);
        logMod("FastCraft", hasFastCraft);
        logMod("CoreTweaks", hasCoreTweaks);

        CrossTie.LOGGER.info("==============================");
    }

    private static void logMod(String modName, boolean detected) {
        CrossTie.LOGGER.info("  {} - {}", modName, detected ? "FOUND" : "Not Found");
    }

    /**
     * RTMが存在するか確認
     */
    public static boolean isRTMPresent() {
        return hasRTM;
    }

    /**
     * いずれかのターゲットModが存在するか確認
     */
    public static boolean hasAnyTargetMod() {
        return hasRTM || hasBamboo || hasOEMod || hasATSAssist || hasSignalController;
    }

    /**
     * 互換性レイヤーが必要か確認
     */
    public static boolean needsCompatibilityLayer() {
        return hasOptiFine || hasAngelica || hasFalseTweaks || hasBeddium || hasFastCraft;
    }

    /**
     * クラスが存在するか確認 (Mixinロード時用)
     */
    public static boolean isClassLoaded(String className) {
        try {
            Class.forName(className, false, ModDetector.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
