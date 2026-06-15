package net.suzumiya.crosstie.mixins.kaizpatch;

import net.suzumiya.crosstie.util.ScriptGlRedirector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks AngelicaCompat.transformScript to redirect GL calls via CrossTie's
 * ScriptGlRedirector instead of — or before — Angelica's own transformation.
 *
 * <p>
 * Only the HEAD injector is used. When it sets a return value the method is
 * cancelled and control returns to the caller, so no RETURN injector is needed
 * here. A RETURN injector would run even after a HEAD-cancel and would apply
 * ScriptGlRedirector a second time on an already-transformed script.
 *
 * <p>
 * ModelPackManagerScriptRedirectMixin also hooks ModelPackManager.getScript
 * at RETURN and applies ScriptGlRedirector once for the non-Angelica path, so
 * the two mixins together cover both code paths without double-transforming.
 */
@Mixin(targets = "jp.kaiz.kaizpatch.compat.AngelicaCompat", remap = false)
public abstract class AngelicaScriptTransformCacheMixin {

    /**
     * Intercepts AngelicaCompat.transformScript at the entry point and returns a
     * CrossTie-redirected script immediately, bypassing Angelica's own GL
     * substitution. The ScriptGlRedirector cache ensures this is O(1) for
     * repeated calls with the same script text.
     */
    @Inject(method = "transformScript", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void crosstie$interceptAngelicaTransform(String script, CallbackInfoReturnable<String> cir) {
        if (script == null) {
            return;
        }
        String transformed = ScriptGlRedirector.transform(script);
        if (transformed != null) {
            cir.setReturnValue(transformed);
        }
    }
}
