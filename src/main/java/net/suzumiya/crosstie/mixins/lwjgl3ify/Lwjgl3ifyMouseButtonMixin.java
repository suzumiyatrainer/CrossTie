package net.suzumiya.crosstie.mixins.lwjgl3ify;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * lwjgl3ify の {@code org.lwjglx.input.Mouse} に対するパッチ。
 *
 * <p>NGTOBuilder 等のスクリプトは {@code Mouse.isButtonDown(n)} でクリックを検知するが、
 * lwjgl3ify 環境では検知できないことがある。ボタンイベントを CrossTie 側でも追跡し、
 * {@code isButtonDown} が false を返す場合に補完する。
 */
@Mixin(targets = "org.lwjglx.input.Mouse", remap = false)
public class Lwjgl3ifyMouseButtonMixin {

    @Inject(method = "addButtonEvent(IZ)V", at = @At("HEAD"), remap = false, require = 0)
    private static void crosstie$trackButton(int button, boolean pressed, CallbackInfo ci) {
        net.suzumiya.crosstie.compat.lwjgl3ify.MouseAdapter.track(button, pressed);
    }

    @Inject(method = "isButtonDown(I)Z", at = @At("RETURN"), remap = false, cancellable = true, require = 0)
    private static void crosstie$fixIsButtonDown(int button, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()
            && net.suzumiya.crosstie.compat.lwjgl3ify.MouseAdapter.mergeIsButtonDownResult(button, false)) {
            cir.setReturnValue(true);
        }
    }
}
