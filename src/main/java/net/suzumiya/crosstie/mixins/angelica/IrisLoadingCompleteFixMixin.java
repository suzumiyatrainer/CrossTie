package net.suzumiya.crosstie.mixins.angelica;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.coderbot.iris.Iris", remap = false)
public class IrisLoadingCompleteFixMixin {

    @Inject(method = "onLoadingComplete", at = @At("HEAD"), remap = false)
    private static void crosstie$forceInitGLSMBridge(CallbackInfo ci) {
        try {
            Class.forName("com.gtnewhorizons.angelica.iris.IrisGLSMBridge");
        } catch (ClassNotFoundException e) {
            // Ignore if Angelica is not fully installed or IrisGLSMBridge is missing
        }
    }
}
