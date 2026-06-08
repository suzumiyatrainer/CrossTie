package net.suzumiya.crosstie.mixins.kaizpatch;

import java.util.HashMap;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "jp.ngt.rtm.rail.util.RailMapCustom", remap = false)
public abstract class RailMapCustomCacheMixin {

    @Unique
    private static final boolean crosstie$railMapCustomCacheEnabled =
            !Boolean.getBoolean("crosstie.disableRailMapCustomCache");

    @Unique
    private Double crosstie$lengthCache;

    @Unique
    private final Map<Long, crosstie$CacheEntry> crosstie$cache = new HashMap<Long, crosstie$CacheEntry>();

    @Unique
    private static class crosstie$CacheEntry {
        double[] pos;
        Double height;
        Float yaw;
        Float pitch;
        Float roll;
    }

    @Unique
    private crosstie$CacheEntry crosstie$getOrCreateEntry(long key) {
        crosstie$CacheEntry entry = crosstie$cache.get(key);
        if (entry == null) {
            entry = new crosstie$CacheEntry();
            crosstie$cache.put(key, entry);
        }
        return entry;
    }

    @Inject(method = "getLength", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$getCachedLength(CallbackInfoReturnable<Double> cir) {
        if (!crosstie$railMapCustomCacheEnabled || crosstie$lengthCache == null) {
            return;
        }
        cir.setReturnValue(crosstie$lengthCache);
    }

    @Inject(method = "getLength", at = @At("RETURN"), require = 0, remap = false)
    private void crosstie$cacheLength(CallbackInfoReturnable<Double> cir) {
        if (crosstie$railMapCustomCacheEnabled) {
            crosstie$lengthCache = cir.getReturnValue();
        }
    }

    @Inject(method = "getRailPos", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$getCachedRailPos(int split, int index, CallbackInfoReturnable<double[]> cir) {
        if (!crosstie$railMapCustomCacheEnabled) {
            return;
        }
        crosstie$CacheEntry entry = crosstie$cache.get(crosstie$key(split, index));
        if (entry != null && entry.pos != null) {
            cir.setReturnValue(entry.pos.clone());
        }
    }

    @Inject(method = "getRailPos", at = @At("RETURN"), require = 0, remap = false)
    private void crosstie$cacheRailPos(int split, int index, CallbackInfoReturnable<double[]> cir) {
        double[] value = cir.getReturnValue();
        if (crosstie$railMapCustomCacheEnabled && value != null) {
            crosstie$getOrCreateEntry(crosstie$key(split, index)).pos = value.clone();
        }
    }

    @Inject(method = "getRailHeight", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$getCachedRailHeight(int split, int index, CallbackInfoReturnable<Double> cir) {
        if (!crosstie$railMapCustomCacheEnabled) {
            return;
        }
        crosstie$CacheEntry entry = crosstie$cache.get(crosstie$key(split, index));
        if (entry != null && entry.height != null) {
            cir.setReturnValue(entry.height);
        }
    }

    @Inject(method = "getRailHeight", at = @At("RETURN"), require = 0, remap = false)
    private void crosstie$cacheRailHeight(int split, int index, CallbackInfoReturnable<Double> cir) {
        if (crosstie$railMapCustomCacheEnabled) {
            crosstie$getOrCreateEntry(crosstie$key(split, index)).height = cir.getReturnValue();
        }
    }

    @Inject(method = "getRailYaw", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$getCachedRailYaw(int split, int index, CallbackInfoReturnable<Float> cir) {
        if (!crosstie$railMapCustomCacheEnabled) {
            return;
        }
        crosstie$CacheEntry entry = crosstie$cache.get(crosstie$key(split, index));
        if (entry != null && entry.yaw != null) {
            cir.setReturnValue(entry.yaw);
        }
    }

    @Inject(method = "getRailYaw", at = @At("RETURN"), require = 0, remap = false)
    private void crosstie$cacheRailYaw(int split, int index, CallbackInfoReturnable<Float> cir) {
        if (crosstie$railMapCustomCacheEnabled) {
            crosstie$getOrCreateEntry(crosstie$key(split, index)).yaw = cir.getReturnValue();
        }
    }

    @Inject(method = "getRailPitch", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$getCachedRailPitch(int split, int index, CallbackInfoReturnable<Float> cir) {
        if (!crosstie$railMapCustomCacheEnabled) {
            return;
        }
        crosstie$CacheEntry entry = crosstie$cache.get(crosstie$key(split, index));
        if (entry != null && entry.pitch != null) {
            cir.setReturnValue(entry.pitch);
        }
    }

    @Inject(method = "getRailPitch", at = @At("RETURN"), require = 0, remap = false)
    private void crosstie$cacheRailPitch(int split, int index, CallbackInfoReturnable<Float> cir) {
        if (crosstie$railMapCustomCacheEnabled) {
            crosstie$getOrCreateEntry(crosstie$key(split, index)).pitch = cir.getReturnValue();
        }
    }

    @Inject(method = "getRailRoll", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$getCachedRailRoll(int split, int index, CallbackInfoReturnable<Float> cir) {
        if (!crosstie$railMapCustomCacheEnabled) {
            return;
        }
        crosstie$CacheEntry entry = crosstie$cache.get(crosstie$key(split, index));
        if (entry != null && entry.roll != null) {
            cir.setReturnValue(entry.roll);
        }
    }

    @Inject(method = "getRailRoll", at = @At("RETURN"), require = 0, remap = false)
    private void crosstie$cacheRailRoll(int split, int index, CallbackInfoReturnable<Float> cir) {
        if (crosstie$railMapCustomCacheEnabled) {
            crosstie$getOrCreateEntry(crosstie$key(split, index)).roll = cir.getReturnValue();
        }
    }

    @Unique
    private static long crosstie$key(int split, int index) {
        return ((long) split << 32) ^ (index & 0xFFFFFFFFL);
    }
}
