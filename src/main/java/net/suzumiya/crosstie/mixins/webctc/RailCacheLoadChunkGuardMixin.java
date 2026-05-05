package net.suzumiya.crosstie.mixins.webctc;

import net.suzumiya.crosstie.CrossTie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * WebCTC の {@code RailCacheData.update()} 内で発生する
 * {@code world.chunkProvider.loadChunk()} をメインスレッド以外から呼ぶことへの警告を追加する。
 *
 * <p>audit §4.4 / §9 P1:
 * "{@code RailCacheData.update()} の {@code loadChunk} は廃止または低頻度キューへ退避する。"
 *
 * <p>Minecraft の {@code ChunkProviderServer.loadChunk()} はメインスレッドから呼ぶことを前提に設計されている。
 * worker thread から呼ぶと ConcurrentModificationException やチャンク状態の不整合を引き起こす。
 * 本 Mixin は呼び出し元スレッドを検出し、メインスレッド以外からの loadChunk 呼び出しに対して
 * スタックトレース付き警告ログを出力する。呼び出し自体はキャンセルしない（安全側の実装）。
 *
 * <p>将来的には loadChunk 自体をキューへ退避させる実装に差し替えることを想定している。
 */
@Mixin(targets = "org.webctc.cache.rail.RailCacheData", remap = false)
public abstract class RailCacheLoadChunkGuardMixin {

    /** Minecraft サーバーメインスレッド名のプレフィックス。 */
    private static final String SERVER_THREAD_PREFIX = "Server thread";

    /**
     * update() の先頭で呼び出しスレッドを確認し、
     * メインスレッド以外からの呼び出しを警告する。
     *
     * <p>ただし、本 Mixin は loadChunk 自体をリダイレクトするのではなく、
     * メソッド全体の入口で確認するに留める。これにより聖域コードへの介入を最小化する。
     */
    @Inject(method = "update", at = @At("HEAD"), require = 0, remap = false)
    private void crosstie$warnIfOffMainThread(CallbackInfo ci) {
        String threadName = Thread.currentThread().getName();
        if (!threadName.startsWith(SERVER_THREAD_PREFIX) && !threadName.startsWith("main")) {
            CrossTie.LOGGER.warn(
                    "[CrossTie] RailCacheData.update() was called from off-thread: '{}'. "
                            + "This may cause loadChunk to run outside the server main thread, "
                            + "leading to chunk corruption or TPS spikes. "
                            + "Consider moving world state access to the main thread.",
                    threadName);
        }
    }
}
