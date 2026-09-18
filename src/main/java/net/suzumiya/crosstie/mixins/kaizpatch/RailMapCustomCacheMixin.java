package net.suzumiya.crosstie.mixins.kaizpatch;

import net.suzumiya.crosstie.utils.concurrent.CrossTieStampedCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicLong;

/**
 * RailMapCustom の各種 getter 結果をキャッシュし、同一エポック内の重複計算を排除する。
 *
 * <p>スレッドセーフ実装: CrossTie 内包の {@link CrossTieStampedCache} を使用。
 * GTNHLib・Angelica の有無を問わず、サーバー・クライアント双方で常時適用される。
 *
 * <p>キャッシュの有効期間は {@link #crosstie$tickEpoch} で管理される。
 * 外部から {@code crosstie$tickEpoch.incrementAndGet()} を呼ぶか、
 * 各 getter が呼ばれるたびにエポックの差分でキャッシュを自動クリアする。
 */
@Mixin(targets = "jp.ngt.rtm.rail.util.RailMapCustom", remap = false)
public abstract class RailMapCustomCacheMixin {

    @Unique
    private static final boolean crosstie$railMapCustomCacheEnabled =
            !Boolean.getBoolean("crosstie.disableRailMapCustomCache");

    /** 最大キャッシュエントリ数（メモリリーク防止） */
    @Unique
    private static final int MAX_CACHE_ENTRIES = 4096;

    /**
     * グローバルエポックカウンタ。インクリメントされるとすべてのインスタンスのキャッシュが
     * 次回アクセス時に自動クリアされる。
     */
    @Unique
    public static final AtomicLong crosstie$tickEpoch = new AtomicLong(0L);

    /** このインスタンスが最後にキャッシュを検証したエポック値 */
    @Unique
    private long crosstie$localEpoch = -1L;

    @Unique
    private Double crosstie$lengthCache;

    /**
     * スレッドセーフな (split, index) → CacheEntry マップ。
     * CrossTie 内包の {@link CrossTieStampedCache} を使用することで、
     * GTNHLib の有無・サーバー/クライアントを問わず常にスレッドセーフ動作を保証する。
     */
    @Unique
    private final CrossTieStampedCache<Long, crosstie$CacheEntry> crosstie$cache =
            new CrossTieStampedCache<>(MAX_CACHE_ENTRIES, k -> new crosstie$CacheEntry(), false);

    @Unique
    private static class crosstie$CacheEntry {
        double[] pos;
        Double height;
        Float yaw;
        Float pitch;
        Float roll;
    }

    @Unique
    private void crosstie$ensureCacheValid() {
        if (!crosstie$railMapCustomCacheEnabled) {
            return;
        }
        long currentEpoch = crosstie$tickEpoch.get();
        if (crosstie$localEpoch != currentEpoch) {
            crosstie$localEpoch = currentEpoch;
            crosstie$cache.clear();
            crosstie$lengthCache = null;
        }
    }

    // ========== getLength ==========

    @Inject(method = "getLength", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$getCachedLength(CallbackInfoReturnable<Double> cir) {
        if (!crosstie$railMapCustomCacheEnabled) return;
        crosstie$ensureCacheValid();
        if (crosstie$lengthCache != null) {
            cir.setReturnValue(crosstie$lengthCache);
        }
    }

    @Inject(method = "getLength", at = @At("RETURN"), require = 0, remap = false)
    private void crosstie$cacheLength(CallbackInfoReturnable<Double> cir) {
        if (crosstie$railMapCustomCacheEnabled) {
            crosstie$lengthCache = cir.getReturnValue();
        }
    }

    // ========== getRailPos ==========

    @Inject(method = "getRailPos", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$getCachedRailPos(int split, int index, CallbackInfoReturnable<double[]> cir) {
        if (!crosstie$railMapCustomCacheEnabled) return;
        crosstie$ensureCacheValid();
        crosstie$CacheEntry entry = crosstie$cache.get(crosstie$key(split, index));
        if (entry != null && entry.pos != null) cir.setReturnValue(entry.pos);
    }

    @Inject(method = "getRailPos", at = @At("RETURN"), require = 0, remap = false)
    private void crosstie$cacheRailPos(int split, int index, CallbackInfoReturnable<double[]> cir) {
        double[] value = cir.getReturnValue();
        if (crosstie$railMapCustomCacheEnabled && value != null) {
            crosstie$cache.get(crosstie$key(split, index)).pos = value;
        }
    }

    // ========== getRailHeight ==========

    @Inject(method = "getRailHeight", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$getCachedRailHeight(int split, int index, CallbackInfoReturnable<Double> cir) {
        if (!crosstie$railMapCustomCacheEnabled) return;
        crosstie$ensureCacheValid();
        crosstie$CacheEntry entry = crosstie$cache.get(crosstie$key(split, index));
        if (entry != null && entry.height != null) cir.setReturnValue(entry.height);
    }

    @Inject(method = "getRailHeight", at = @At("RETURN"), require = 0, remap = false)
    private void crosstie$cacheRailHeight(int split, int index, CallbackInfoReturnable<Double> cir) {
        if (crosstie$railMapCustomCacheEnabled) {
            crosstie$cache.get(crosstie$key(split, index)).height = cir.getReturnValue();
        }
    }

    // ========== getRailYaw ==========

    @Inject(method = "getRailYaw", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$getCachedRailYaw(int split, int index, CallbackInfoReturnable<Float> cir) {
        if (!crosstie$railMapCustomCacheEnabled) return;
        crosstie$ensureCacheValid();
        crosstie$CacheEntry entry = crosstie$cache.get(crosstie$key(split, index));
        if (entry != null && entry.yaw != null) cir.setReturnValue(entry.yaw);
    }

    @Inject(method = "getRailYaw", at = @At("RETURN"), require = 0, remap = false)
    private void crosstie$cacheRailYaw(int split, int index, CallbackInfoReturnable<Float> cir) {
        if (crosstie$railMapCustomCacheEnabled) {
            crosstie$cache.get(crosstie$key(split, index)).yaw = cir.getReturnValue();
        }
    }

    // ========== getRailPitch ==========

    @Inject(method = "getRailPitch", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$getCachedRailPitch(int split, int index, CallbackInfoReturnable<Float> cir) {
        if (!crosstie$railMapCustomCacheEnabled) return;
        crosstie$ensureCacheValid();
        crosstie$CacheEntry entry = crosstie$cache.get(crosstie$key(split, index));
        if (entry != null && entry.pitch != null) cir.setReturnValue(entry.pitch);
    }

    @Inject(method = "getRailPitch", at = @At("RETURN"), require = 0, remap = false)
    private void crosstie$cacheRailPitch(int split, int index, CallbackInfoReturnable<Float> cir) {
        if (crosstie$railMapCustomCacheEnabled) {
            crosstie$cache.get(crosstie$key(split, index)).pitch = cir.getReturnValue();
        }
    }

    // ========== getRailRoll ==========

    @Inject(method = "getRailRoll", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$getCachedRailRoll(int split, int index, CallbackInfoReturnable<Float> cir) {
        if (!crosstie$railMapCustomCacheEnabled) return;
        crosstie$ensureCacheValid();
        crosstie$CacheEntry entry = crosstie$cache.get(crosstie$key(split, index));
        if (entry != null && entry.roll != null) cir.setReturnValue(entry.roll);
    }

    @Inject(method = "getRailRoll", at = @At("RETURN"), require = 0, remap = false)
    private void crosstie$cacheRailRoll(int split, int index, CallbackInfoReturnable<Float> cir) {
        if (crosstie$railMapCustomCacheEnabled) {
            crosstie$cache.get(crosstie$key(split, index)).roll = cir.getReturnValue();
        }
    }

    @Unique
    private static long crosstie$key(int split, int index) {
        return ((long) split << 32) ^ (index & 0xFFFFFFFFL);
    }
}