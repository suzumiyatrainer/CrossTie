package net.suzumiya.crosstie.mixins.kaizpatch;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.suzumiya.crosstie.utils.ScriptGlRedirector;

@Mixin(targets = "jp.ngt.rtm.modelpack.ModelPackManager", remap = false)
public abstract class ModelPackManagerScriptRedirectMixin {

    @Inject(method = "getScript", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void crosstie$redirectScriptGlCalls(String fileName, CallbackInfoReturnable<String> cir) {
        String script = cir.getReturnValue();
        if (script != null) {
            cir.setReturnValue(ScriptGlRedirector.transform(script));
        }
    }
}
