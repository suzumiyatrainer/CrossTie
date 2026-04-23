package net.suzumiya.crosstie.util;

/**
 * hi03ExpressRailway 用の描画コンテキストを管理するクラス。
 *
 * DisplayList コンパイル中に hi03ExpressRailway の描画を通常の OpenGL 経路へ
 * 切り替えるために使います。Angelica のディスプレイリスト処理と干渉しないよう、
 * 状態をスレッドローカルで保持します。
 */
public class Hi03ExpressRailwayContext {

    private static final int FLAG_ACTIVE = 1;
    private static final int FLAG_SKIPPING_DISPLAY_LIST = 1 << 1;
    private static final int FLAG_USING_LEGACY_DISPLAY_LIST = 1 << 2;
    private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);

    private Hi03ExpressRailwayContext() {
    }

    /**
     * コンテキストが有効かを返す。
     *
     * @return hi03ExpressRailway の描画中なら true
     */
    public static boolean isActive() {
        return hasFlag(FLAG_ACTIVE);
    }

    /**
     * hi03ExpressRailway の描画コンテキストに入る。
     */
    public static void enter() {
        STATE.get().flags = FLAG_ACTIVE;
    }

    /**
     * hi03ExpressRailway の描画コンテキストを抜ける。
     */
    public static void exit() {
        reset();
    }

    /**
     * ディスプレイリストのコンパイルをスキップするかを返す。
     *
     * @return スキップ中なら true
     */
    public static boolean isSkippingDisplayList() {
        return hasFlag(FLAG_SKIPPING_DISPLAY_LIST);
    }

    /**
     * ディスプレイリストのコンパイルをスキップするかを設定する。
     *
     * @param skipping スキップするなら true
     */
    public static void setSkippingDisplayList(boolean skipping) {
        setFlag(FLAG_SKIPPING_DISPLAY_LIST, skipping);
    }

    /**
     * 旧来の OpenGL ディスプレイリストを使っているかを返す。
     *
     * @return 旧経路を使っているなら true
     */
    public static boolean isUsingLegacyDisplayList() {
        return hasFlag(FLAG_USING_LEGACY_DISPLAY_LIST);
    }

    /**
     * 旧来のディスプレイリストを使っているかを設定する。
     *
     * @param using 旧経路を使うなら true
     */
    public static void setUsingLegacyDisplayList(boolean using) {
        setFlag(FLAG_USING_LEGACY_DISPLAY_LIST, using);
    }

    /**
     * コンテキストをリセットする。エラー回復時にも使う。
     */
    public static void reset() {
        STATE.get().flags = 0;
    }

    private static boolean hasFlag(int flag) {
        return (STATE.get().flags & flag) != 0;
    }

    private static void setFlag(int flag, boolean enabled) {
        State state = STATE.get();
        if (enabled) {
            state.flags |= flag;
            return;
        }
        state.flags &= ~flag;
    }

    private static final class State {
        private int flags;
    }
}
