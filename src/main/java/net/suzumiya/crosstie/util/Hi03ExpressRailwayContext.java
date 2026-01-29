package net.suzumiya.crosstie.util;

/**
 * hi03ExpressRailwayレンダリングコンテキストトラッカー
 * 
 * DisplayListコンパイル内でPolygonRendererを使用するhi03ExpressRailwayモデルの
 * レンダリング中かどうかを追跡します。
 * 
 * このコンテキストがアクティブな場合、Angelicaのディスプレイリスト最適化をバイパスし、
 * ネイティブOpenGLのディスプレイリストを使用します。
 */
public class Hi03ExpressRailwayContext {

    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> SKIPPING_DISPLAY_LIST = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> USING_LEGACY_DISPLAY_LIST = ThreadLocal.withInitial(() -> false);

    /**
     * コンテキストがアクティブかどうかを返します
     * 
     * @return hi03ExpressRailwayレンダリング中の場合true
     */
    public static boolean isActive() {
        return ACTIVE.get();
    }

    /**
     * hi03ExpressRailwayレンダリングコンテキストに入ります
     */
    public static void enter() {
        ACTIVE.set(true);
    }

    /**
     * hi03ExpressRailwayレンダリングコンテキストを終了します
     */
    public static void exit() {
        ACTIVE.set(false);
    }

    /**
     * ディスプレイリストコンパイルをスキップ中かどうかを返します
     * 
     * @return スキップ中の場合true
     */
    public static boolean isSkippingDisplayList() {
        return SKIPPING_DISPLAY_LIST.get();
    }

    /**
     * ディスプレイリストコンパイルのスキップ状態を設定
     * 
     * @param skipping スキップ中かどうか
     */
    public static void setSkippingDisplayList(boolean skipping) {
        SKIPPING_DISPLAY_LIST.set(skipping);
    }

    /**
     * レガシー(ネイティブOpenGL)ディスプレイリストを使用中かどうかを返します
     * 
     * @return ネイティブOpenGLディスプレイリストを使用中の場合true
     */
    public static boolean isUsingLegacyDisplayList() {
        return USING_LEGACY_DISPLAY_LIST.get();
    }

    /**
     * レガシーディスプレイリスト使用状態を設定
     * 
     * @param using レガシーディスプレイリストを使用中かどうか
     */
    public static void setUsingLegacyDisplayList(boolean using) {
        USING_LEGACY_DISPLAY_LIST.set(using);
    }

    /**
     * コンテキストをリセットします（エラーリカバリ用）
     */
    public static void reset() {
        ACTIVE.remove();
        SKIPPING_DISPLAY_LIST.remove();
        USING_LEGACY_DISPLAY_LIST.remove();
    }
}
