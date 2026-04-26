package net.suzumiya.crosstie.mixins.ngtlib;

import net.suzumiya.crosstie.config.CrossTieConfig;
import net.suzumiya.crosstie.util.ScriptFunctionCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.script.ScriptEngine;

@Mixin(targets = "jp.ngt.ngtlib.io.ScriptUtil", remap = false)
public abstract class ScriptFunctionCacheMixin {

    @Inject(method = "doScriptFunction", at = @At("HEAD"), cancellable = true)
    private static void crosstie$invokeWithCachedInvocable(ScriptEngine scriptEngine, String function, Object[] args,
            CallbackInfoReturnable<Object> cir) {
        if (!CrossTieConfig.enableScriptRenderFunctionCache) {
            return;
        }
        cir.setReturnValue(ScriptFunctionCache.invoke(scriptEngine, function, args));
    }
}

