package net.suzumiya.crosstie.utils;

import java.util.concurrent.atomic.AtomicLong;

import net.suzumiya.crosstie.CrossTie;
import net.suzumiya.crosstie.CrossTieConfig;

/**
 * P0 計測ユーティリティ (audit §9 P0)
 *
 * <p>
 * 各カウンタは全てアトミックであり、マルチスレッドから安全にインクリメントできる。 診断ログは {@link #logAndReset()}
 * を定期的に呼ぶことで出力される。 本クラス自体は外部 mod クラスへの依存を持たないため、任意の Mixin から利用できる。
 *
 * <p>
 * 有効化: config/crosstie.cfg の enableDiagnostics=true、または システムプロパティ
 * crosstie.diagnostics=true を設定する。
 */
public final class CrossTieDiagnostics {

    /** 診断を有効化するシステムプロパティキー。 */
    public static final String PROP_ENABLED = "crosstie.diagnostics";

    private static final boolean ENABLED_BY_PROPERTY = Boolean.getBoolean(PROP_ENABLED);

    // ---- カウンタ群 ---- //

    /** ScriptUtil.doScriptFunction("render") 呼び出し数 */
    public static final AtomicLong nashornRenderCalls = new AtomicLong();

    /** GLHelper.startCompile() 発生数 */
    public static final AtomicLong displayListCompiles = new AtomicLong();

    /** NGTTessellator.draw() Angelica fallback 呼び出し数 */
    public static final AtomicLong ngtTessellatorDraws = new AtomicLong();

    /** world.setBlock() 呼び出し数 (CrossTie 管理下のもの) */
    public static final AtomicLong blockUpdates = new AtomicLong();

    /** world.markBlockForUpdate() 呼び出し数 */
    public static final AtomicLong markBlockForUpdateCalls = new AtomicLong();

    /** world.notifyBlockChange() 呼び出し数 */
    public static final AtomicLong notifyBlockChangeCalls = new AtomicLong();

    /** 差分チェックでスキップされた setBlock 数 */
    public static final AtomicLong skippedSetBlockCalls = new AtomicLong();

    /** reflection field lookup キャッシュヒット数 */
    public static final AtomicLong reflectionCacheHits = new AtomicLong();

    /** reflection field lookup キャッシュミス数 */
    public static final AtomicLong reflectionCacheMisses = new AtomicLong();

    private CrossTieDiagnostics() {
    }

    /**
     * 診断ログが有効かどうかを返す。
     *
     * <p>
     * システムプロパティ {@code crosstie.diagnostics=true} が設定されていればそれを優先。 設定されていない場合は
     * {@link CrossTieConfig#enableDiagnostics} の値を参照する。 config は preInit
     * 後に読み込まれるため、preInit より前にこのメソッドが呼ばれた場合は デフォルトの false を返すことに注意。
     *
     * @return 診断が有効なら true
     */
    public static boolean isEnabled() {
        return ENABLED_BY_PROPERTY || CrossTieConfig.enableDiagnostics;
    }

    /**
     * 現在の全カウンタ値をログ出力し、カウンタをリセットする。 サーバー/クライアント tick の末尾から定期的に呼ぶことを想定する。
     */
    public static void logAndReset() {
        if (!isEnabled()) {
            return;
        }

        long nashorn = nashornRenderCalls.getAndSet(0);
        long dlCompile = displayListCompiles.getAndSet(0);
        long ngtDraw = ngtTessellatorDraws.getAndSet(0);
        long setBlock = blockUpdates.getAndSet(0);
        long markBlock = markBlockForUpdateCalls.getAndSet(0);
        long notifyBlock = notifyBlockChangeCalls.getAndSet(0);
        long skipped = skippedSetBlockCalls.getAndSet(0);
        long cacheHit = reflectionCacheHits.getAndSet(0);
        long cacheMiss = reflectionCacheMisses.getAndSet(0);

        CrossTie.LOGGER.info("[CrossTie Diagnostics] nashorn_render={} dl_compile={} ngt_draw={}"
                + " setBlock={} markBlock={} notifyBlock={} skipped_setBlock={}" + " refCache_hit={} refCache_miss={}",
                nashorn, dlCompile, ngtDraw, setBlock, markBlock, notifyBlock, skipped, cacheHit, cacheMiss);
    }
}