package net.suzumiya.crosstie.mixins.macros;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.eq2online.macros.input.InputHandler", remap = false)
public abstract class MacroInputHandlerMixin {

    @Shadow
    private static Object keybindSneak;
    @Shadow
    private static Object keybindActivate;

    /**
     * 1. クラス初期化時のミラーリング（起動時のnullクラッシュ防止）
     */
    @Inject(method = "<clinit>", at = @At("TAIL"), remap = false)
    private static void crosstie$initMissingSneakKeyBinding(CallbackInfo ci) {
        try {
            if (keybindSneak == null && keybindActivate != null) {
                keybindSneak = keybindActivate;
                System.out.println(
                        "[CrossTie] InputHandler <clinit> mirrored keybindActivate to keybindSneak successfully.");
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 2. 【重要修正】諸悪の根源を完全無害化するキャンセルフック
     * ターゲットメソッドが引数に `Minecraft` を取るため、
     * メソッドの第一引数に `Object` (Minecraft用) を追加してシグネチャを一致させます。
     */
    @Inject(method = "update(Lnet/minecraft/client/Minecraft;)V", at = @At("HEAD"), remap = false, cancellable = true)
    private static void crosstie$cancelInputUpdate(Object minecraft, CallbackInfo ci) {
        ci.cancel();
    }
}