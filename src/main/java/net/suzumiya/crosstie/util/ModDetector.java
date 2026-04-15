package net.suzumiya.crosstie.util;

import cpw.mods.fml.common.Loader;
import net.suzumiya.crosstie.CrossTie;

/**
 * 起動時に導入済み Mod を検出するユーティリティ。
 *
 * 必要な Mod の有無を自動判定して、互換レイヤーの有効・無効を切り替えます。
 */
public class ModDetector {

    // 対象 Mod
    public static boolean hasRTM = false;
    public static boolean hasBamboo = false;
    public static boolean hasOEMod = false;
    public static boolean hasATSAssist = false;
    public static boolean hasSignalController = false;

    // 軽量化 Mod（互換性確認用）
    public static boolean hasOptiFine = false;
    public static boolean hasAngelica = false;
    public static boolean hasFalseTweaks = false;
    public static boolean hasBeddium = false;
    public static boolean hasSwanSong = false;
    public static boolean hasFastCraft = false;
    public static boolean hasCoreTweaks = false;

    /**
     * すべての対象 Mod の導入有無を確認する。
     */
    public static void detectMods() {
        // 対象 Mod を検出する
        hasRTM = Loader.isModLoaded("RTM");
        hasBamboo = Loader.isModLoaded("Bamboo");
        hasOEMod = Loader.isModLoaded("OEMod");
        hasATSAssist = Loader.isModLoaded("ATSAssist");
        hasSignalController = Loader.isModLoaded("SignalController");

        // 軽量化 Mod を検出する
        hasAngelica = Loader.isModLoaded("angelica");
        hasFalseTweaks = Loader.isModLoaded("falsetweaks");
        hasBeddium = Loader.isModLoaded("beddium");
        hasSwanSong = Loader.isModLoaded("swansong");
        hasCoreTweaks = Loader.isModLoaded("coretweaks");

        // OptiFine は ModID を持たないため、反射で検出する
        hasOptiFine = checkOptiFine();

        // FastCraft も反射で検出する
        hasFastCraft = checkFastCraft();
    }

    /**
     * OptiFine の導入有無を反射で確認する。
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
     * FastCraft の導入有無を反射で確認する。
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
     * 検出した Mod をログに出力する。
     */
    public static void logDetectedMods() {
        CrossTie.LOGGER.info("=== CrossTie Mod 検出 ===");

        CrossTie.LOGGER.info("対象 Mod:");
        logMod("RTM/KaizPatchX", hasRTM);
        logMod("Bamboo", hasBamboo);
        logMod("OEMod", hasOEMod);
        logMod("ATSAssist", hasATSAssist);
        logMod("SignalController", hasSignalController);

        CrossTie.LOGGER.info("軽量化 Mod（互換レイヤー）:");
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
        CrossTie.LOGGER.info("  {} - {}", modName, detected ? "検出" : "未検出");
    }

    /**
     * RTM が導入されているかを返す。
     */
    public static boolean isRTMPresent() {
        return hasRTM;
    }

    /**
     * いずれかの対象 Mod が導入されているかを返す。
     */
    public static boolean hasAnyTargetMod() {
        return hasRTM || hasBamboo || hasOEMod || hasATSAssist || hasSignalController;
    }

    /**
     * 互換性レイヤーが必要かを返す。
     */
    public static boolean needsCompatibilityLayer() {
        return hasOptiFine || hasAngelica || hasFalseTweaks || hasBeddium || hasFastCraft;
    }

    /**
     * 指定クラスが読み込まれているかを確認する。（Mixin の条件分岐用）
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
