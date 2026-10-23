package net.suzumiya.crosstie.mixins.lwjgl3ify;

import net.suzumiya.crosstie.compat.lwjgl3ify.ImeCompat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * クリック位置を記録し、GuiTextField 以外の独自テキスト欄でも IME 候補窓を
 * おおよそ正しい位置に出すためのフォールバック情報とする。
 */
@Mixin(targets = "org.lwjglx.input.Mouse", remap = false)
public class Lwjgl3ifyImeMouseMixin {

    @Inject(method = "addButtonEvent(IZ)V", at = @At("HEAD"), remap = false, require = 0)
    private static void crosstie$recordClick(int button, boolean pressed, CallbackInfo ci) {
        if (pressed) {
            ImeCompat.onClick(org.lwjgl.input.Mouse.getX(), org.lwjgl.input.Mouse.getY());
        }
    }
}
