package net.suzumiya.crosstie.mixins.kaizpatch;

import net.suzumiya.crosstie.util.ScriptGlRedirector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "jp.kaiz.kaizpatch.compat.AngelicaCompat", remap = false)
public abstract class AngelicaScriptTransformCacheMixin {

    @Inject(method = "transformScript", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void crosstie$getCachedTransform(String script, CallbackInfoReturnable<String> cir) {
        if (script == null) {
            return;
        }
        String transformed = ScriptGlRedirector.transform(script);
        if (transformed != null) {
            cir.setReturnValue(transformed);
        }
    }

    @Inject(method = "transformScript", at = @At("RETURN"), require = 0, remap = false)
    private static void crosstie$cacheTransform(String script, CallbackInfoReturnable<String> cir) {
        if (script == null || cir.getReturnValue() == null) {
            return;
        }
        cir.setReturnValue(ScriptGlRedirector.transform(cir.getReturnValue()));
    }
}
