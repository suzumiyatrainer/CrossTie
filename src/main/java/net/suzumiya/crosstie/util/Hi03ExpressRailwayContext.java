package net.suzumiya.crosstie.util;

/**
 * hi03ExpressRailway 用の描画コンテキストを管理するクラス。
 *
 * DisplayList コンパイル中に hi03ExpressRailway の描画を通常の OpenGL 経路へ
 * 切り替えるために使います。Angelica のディスプレイリスト処理と干渉しないよう、
 * 状態をスレッドローカルで保持します。
 */
public class Hi03ExpressRailwayContext {

    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> SKIPPING_DISPLAY_LIST = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> USING_LEGACY_DISPLAY_LIST = ThreadLocal.withInitial(() -> false);

    /**
     * コンテキストが有効かを返す。
     *
     * @return hi03ExpressRailway の描画中なら true
     */
    public static boolean isActive() {
        return ACTIVE.get();
    }

    /**
     * hi03ExpressRailway の描画コンテキストに入る。
     */
    public static void enter() {
        ACTIVE.set(true);
        SKIPPING_DISPLAY_LIST.set(false);
        USING_LEGACY_DISPLAY_LIST.set(false);
    }

    /**
     * hi03ExpressRailway の描画コンテキストを抜ける。
     */
    public static void exit() {
        ACTIVE.set(false);
        SKIPPING_DISPLAY_LIST.set(false);
        USING_LEGACY_DISPLAY_LIST.set(false);
    }

    /**
     * ディスプレイリストのコンパイルをスキップするかを返す。
     *
     * @return スキップ中なら true
     */
    public static boolean isSkippingDisplayList() {
        return SKIPPING_DISPLAY_LIST.get();
    }

    /**
     * ディスプレイリストのコンパイルをスキップするかを設定する。
     *
     * @param skipping スキップするなら true
     */
    public static void setSkippingDisplayList(boolean skipping) {
        SKIPPING_DISPLAY_LIST.set(skipping);
    }

    /**
     * 旧来の OpenGL ディスプレイリストを使っているかを返す。
     *
     * @return 旧経路を使っているなら true
     */
    public static boolean isUsingLegacyDisplayList() {
        return USING_LEGACY_DISPLAY_LIST.get();
    }

    /**
     * 旧来のディスプレイリストを使っているかを設定する。
     *
     * @param using 旧経路を使うなら true
     */
    public static void setUsingLegacyDisplayList(boolean using) {
        USING_LEGACY_DISPLAY_LIST.set(using);
    }

    /**
     * コンテキストをリセットする。エラー回復時にも使う。
     */
    public static void reset() {
        ACTIVE.remove();
        SKIPPING_DISPLAY_LIST.remove();
        USING_LEGACY_DISPLAY_LIST.remove();
    }
}
