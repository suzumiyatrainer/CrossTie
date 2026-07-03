package net.suzumiya.crosstie.util;

/**
 * RTM {@code Parts.render()} の実行コンテキストをスレッドローカルで管理するユーティリティ。
 *
 * <p>
 * {@link net.suzumiya.crosstie.mixins.angelica.AngelicaRenderGlobalDisplayListCrashMixin}
 * はもともと {@code Thread.currentThread().getStackTrace()} でコールスタックを走査して
 * {@code Parts.render()} の中にいるかを判断していたが、これは JVM で最も重い操作の一つであり
 * {@code glNewList} のたびに実行されるため深刻なパフォーマンス問題を引き起こしていた。
 *
 * <p>
 * 代わりに {@code Parts.render()} の HEAD/RETURN に Mixin インジェクションで
 * {@link #enter()} / {@link #exit()} を呼び出し、ネスト深度カウンタで在否を管理する。
 * これによりスタックトレースウォーキングが完全に不要になる。
 */
public final class CrossTiePartsRenderContext {

    /**
     * ネスト深度カウンタ。{@code Parts.render()} が呼び出されるたびにインクリメントされ、
     * 戻るたびにデクリメントされる。0 より大きい場合はコンテキスト内にいる。
     */
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private CrossTiePartsRenderContext() {}

    /**
     * {@code Parts.render()} の HEAD で呼び出す。
     */
    public static void enter() {
        DEPTH.set(DEPTH.get() + 1);
    }

    /**
     * {@code Parts.render()} の RETURN/THROW で呼び出す。
     */
    public static void exit() {
        int d = DEPTH.get();
        if (d > 0) {
            DEPTH.set(d - 1);
        }
    }

    /**
     * 現在のスレッドが {@code Parts.render()} の呼び出しスタック内にあるかどうかを返す。
     *
     * @return {@code Parts.render()} のコンテキスト内なら {@code true}
     */
    public static boolean isInsidePartsRender() {
        return DEPTH.get() > 0;
    }
}
