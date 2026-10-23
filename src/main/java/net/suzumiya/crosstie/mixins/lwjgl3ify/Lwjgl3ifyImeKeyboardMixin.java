package net.suzumiya.crosstie.mixins.lwjgl3ify;

import net.suzumiya.crosstie.compat.lwjgl3ify.ImeCompat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * lwjgl3ify の {@code org.lwjglx.input.Keyboard} に対する IME 補正。
 * TextInput を自動有効化すると ASCII がキーイベントとテキストイベントの両方から届くため、
 * キーイベントで配送済みの文字をテキストイベント側で破棄する。
 */
@Mixin(targets = "org.lwjglx.input.Keyboard", remap = false)
public class Lwjgl3ifyImeKeyboardMixin {

    @Inject(method = "addSdlKeyEvent", at = @At("HEAD"), remap = false, require = 0)
    private static void crosstie$recordKeyChar(int key, int scancode, @Coerce Object action, int mods, int c,
        long nanoTime, CallbackInfo ci) {
        if (c != 0 && !"RELEASED".equals(String.valueOf(action))) {
            ImeCompat.recordKeyChar(c);
        }
    }

    @Inject(method = "addCharEvent", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void crosstie$dropDuplicateChar(int key, int c, CallbackInfo ci) {
        if (ImeCompat.consumeDuplicateChar(c)) {
            ci.cancel();
        }
    }
}
