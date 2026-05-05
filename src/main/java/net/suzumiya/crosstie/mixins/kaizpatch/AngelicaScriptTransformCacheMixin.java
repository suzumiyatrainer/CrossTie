package net.suzumiya.crosstie.mixins.kaizpatch;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "jp.kaiz.kaizpatch.compat.AngelicaCompat", remap = false)
public abstract class AngelicaScriptTransformCacheMixin {

    @Unique
    private static final int CROSSTIE_MAX_SCRIPT_CACHE_SIZE = 512;

    @Unique
    private static final Map<String, String> crosstie$transformedScripts = Collections.synchronizedMap(
            new LinkedHashMap<String, String>(64, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > CROSSTIE_MAX_SCRIPT_CACHE_SIZE;
                }
            });

    @Inject(method = "transformScript", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void crosstie$getCachedTransform(String script, CallbackInfoReturnable<String> cir) {
        if (script == null) {
            return;
        }
        String transformed = crosstie$transformedScripts.get(script);
        if (transformed != null) {
            cir.setReturnValue(transformed);
        }
    }

    @Inject(method = "transformScript", at = @At("RETURN"), require = 0, remap = false)
    private static void crosstie$cacheTransform(String script, CallbackInfoReturnable<String> cir) {
        if (script != null && cir.getReturnValue() != null) {
            crosstie$transformedScripts.put(script, cir.getReturnValue());
        }
    }
}
