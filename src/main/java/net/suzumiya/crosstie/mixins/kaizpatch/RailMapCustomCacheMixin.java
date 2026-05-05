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
    private final Map<Long, double[]> crosstie$posCache = new HashMap<Long, double[]>();

    @Unique
    private final Map<Long, Double> crosstie$heightCache = new HashMap<Long, Double>();

    @Unique
    private final Map<Long, Float> crosstie$yawCache = new HashMap<Long, Float>();

    @Unique
    private final Map<Long, Float> crosstie$pitchCache = new HashMap<Long, Float>();

    @Unique
    private final Map<Long, Float> crosstie$rollCache = new HashMap<Long, Float>();

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
        double[] cached = crosstie$posCache.get(crosstie$key(split, index));
        if (cached != null) {
            cir.setReturnValue(cached.clone());
        }
    }

    @Inject(method = "getRailPos", at = @At("RETURN"), require = 0, remap = false)
    private void crosstie$cacheRailPos(int split, int index, CallbackInfoReturnable<double[]> cir) {
        double[] value = cir.getReturnValue();
        if (crosstie$railMapCustomCacheEnabled && value != null) {
            crosstie$posCache.put(crosstie$key(split, index), value.clone());
        }
    }

    @Inject(method = "getRailHeight", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$getCachedRailHeight(int split, int index, CallbackInfoReturnable<Double> cir) {
        if (!crosstie$railMapCustomCacheEnabled) {
            return;
        }
        Double cached = crosstie$heightCache.get(crosstie$key(split, index));
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "getRailHeight", at = @At("RETURN"), require = 0, remap = false)
    private void crosstie$cacheRailHeight(int split, int index, CallbackInfoReturnable<Double> cir) {
        if (crosstie$railMapCustomCacheEnabled) {
            crosstie$heightCache.put(crosstie$key(split, index), cir.getReturnValue());
        }
    }

    @Inject(method = "getRailYaw", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$getCachedRailYaw(int split, int index, CallbackInfoReturnable<Float> cir) {
        if (!crosstie$railMapCustomCacheEnabled) {
            return;
        }
        Float cached = crosstie$yawCache.get(crosstie$key(split, index));
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "getRailYaw", at = @At("RETURN"), require = 0, remap = false)
    private void crosstie$cacheRailYaw(int split, int index, CallbackInfoReturnable<Float> cir) {
        if (crosstie$railMapCustomCacheEnabled) {
            crosstie$yawCache.put(crosstie$key(split, index), cir.getReturnValue());
        }
    }

    @Inject(method = "getRailPitch", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$getCachedRailPitch(int split, int index, CallbackInfoReturnable<Float> cir) {
        if (!crosstie$railMapCustomCacheEnabled) {
            return;
        }
        Float cached = crosstie$pitchCache.get(crosstie$key(split, index));
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "getRailPitch", at = @At("RETURN"), require = 0, remap = false)
    private void crosstie$cacheRailPitch(int split, int index, CallbackInfoReturnable<Float> cir) {
        if (crosstie$railMapCustomCacheEnabled) {
            crosstie$pitchCache.put(crosstie$key(split, index), cir.getReturnValue());
        }
    }

    @Inject(method = "getRailRoll", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$getCachedRailRoll(int split, int index, CallbackInfoReturnable<Float> cir) {
        if (!crosstie$railMapCustomCacheEnabled) {
            return;
        }
        Float cached = crosstie$rollCache.get(crosstie$key(split, index));
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "getRailRoll", at = @At("RETURN"), require = 0, remap = false)
    private void crosstie$cacheRailRoll(int split, int index, CallbackInfoReturnable<Float> cir) {
        if (crosstie$railMapCustomCacheEnabled) {
            crosstie$rollCache.put(crosstie$key(split, index), cir.getReturnValue());
        }
    }

    @Unique
    private static Long crosstie$key(int split, int index) {
        return Long.valueOf(((long) split << 32) ^ (index & 0xFFFFFFFFL));
    }
}
