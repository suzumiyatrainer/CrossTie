package net.suzumiya.crosstie.utils;

import net.suzumiya.crosstie.utils.js.CrossTieConductorClientUtils;
import net.suzumiya.crosstie.utils.js.CrossTieConductorUtils;
import net.suzumiya.crosstie.utils.js.CrossTieVersionChecker;

/**
 * CrossTie JSブリッジ — サーバー側Nashornのクラスローダー問題を回避するファサードクラス。
 *
 * <p>
 * <b>問題の背景:</b><br>
 * RTM車両パックのサーバーJSが {@code importPackage()} でCrossTieのユーティリティを
 * 参照しようとしても、サーバー側Nashornが使うクラスローダーはLaunchClassLoaderを 介さないため
 * {@code CrossTieArudenUtils} / {@code CrossTieTascUtils} を解決できず
 * <b>無言で失敗する</b>。
 *
 * <p>
 * <b>解決策:</b><br>
 * このクラスは {@code net.suzumiya.crosstie.utils} パッケージに置かれており、 JS側は
 * {@code Java.type("net.suzumiya.crosstie.utils.CrossTieBridge")} の
 * <b>1行だけ</b>で完全にバインドできる。 バージョン確認・ConductorUtils・TascUtils の全メソッドに委譲する。
 *
 * <p>
 * <b>JS側使用例:</b>
 * 
 * <pre>{@code
 * var CrossTieBridge = Java.type("net.suzumiya.crosstie.utils.CrossTieBridge");
 * if (CrossTieBridge.exists() && CrossTieBridge.isAtLeast("1.0.0-Alpha13")) {
 *     CrossTieBridge.processConductorServer(entity, dataMap, ...);
 *     CrossTieBridge.processTasc(entity, dataMap);
 * }
 * }</pre>
 */
public final class CrossTieBridge {

    private CrossTieBridge() {
        // ユーティリティクラス。インスタンス化不可
    }

    // =============================================================
    // バージョン確認 (CrossTieVersionChecker への委譲)
    // =============================================================

    /** JS側の存在確認用。このメソッドが呼べた時点でCrossTieは存在する。 */
    public static boolean exists() {
        return CrossTieVersionChecker.exists();
    }

    /** "CrossTie-1.0.0-Alpha13" 形式のフルバージョン文字列を返す。 */
    public static String getVersion() {
        return CrossTieVersionChecker.getVersion();
    }

    /** 接頭辞なしの生バージョン文字列を返す。 */
    public static String getRawVersion() {
        return CrossTieVersionChecker.getRawVersion();
    }

    /**
     * 現在のCrossTieバージョンが指定バージョン以上かどうかを判定する。
     * 
     * @param requiredRawVersion 例: "1.0.0-Alpha13" (接頭辞なし)
     */
    public static boolean isAtLeast(String requiredRawVersion) {
        return CrossTieVersionChecker.isAtLeast(requiredRawVersion);
    }

    // =============================================================
    // 車掌システム (CrossTieArudenUtils への委譲)
    // =============================================================

    /**
     * サーバーサイドの車掌処理。onUpdate() から毎tickで呼ぶ。
     */
    public static void processConductorServer(Object entityObj, Object dataMapObj, boolean isDoubleCab, double minZ,
            double maxZ, double posX, String syncKeysCsv, String syncKeysBoolCsv) {
        CrossTieConductorUtils.processConductorServer(entityObj, dataMapObj, isDoubleCab, minZ, maxZ, posX, syncKeysCsv,
                syncKeysBoolCsv);
    }

    /** クライアントサイドのキー入力処理。onRender() から毎フレームで呼ぶ。 */
    public static void processConductorRender(Object entityObj, Object dataMapObj, int buzzer, int bell, int door,
            int next, int prev, int ann, int eb, int melody, int tojime, int fd) {
        CrossTieConductorClientUtils.processConductorRender(entityObj, dataMapObj, buzzer, bell, door, next, prev, ann,
                eb, melody, tojime, fd);
    }

    /** クライアントサイドのサウンド制御処理。 */
    public static void processConductorSound(Object su, Object dataMapObj, String buzzerPath, String bellPath,
            String dmain, String melo, float vol) {
        CrossTieConductorUtils.processConductorSound(su, dataMapObj, buzzerPath, bellPath, dmain, melo, vol);
    }

    // =============================================================
    // TASCシステム (CrossTieTascUtils への委譲)
    // =============================================================

    /**
     * サーバーサイドのTASC/SPCS処理。onUpdate() から毎tickで呼ぶ。
     */
    public static void processTasc(Object entityObj, Object dataMapObj) {
        CrossTieTascUtils.processTasc(entityObj, dataMapObj);
    }
}