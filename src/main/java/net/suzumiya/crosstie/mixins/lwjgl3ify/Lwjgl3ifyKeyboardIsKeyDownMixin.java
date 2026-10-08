package net.suzumiya.crosstie.mixins.lwjgl3ify;

import java.nio.ByteBuffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * lwjgl3ify の {@code org.lwjglx.input.Keyboard.isKeyDown(int)} に Mixin を適用し、
 * キーマッピングの問題（特に日本語キーボード環境）を修正するパッチ。
 *
 * <h3>問題の概要</h3>
 * NGTOBuilder は {@code Keyboard.isKeyDown(keyCode)} を毎フレーム呼び出して
 * キー入力を検出する。lwjgl3ify の実装では:
 * 1. {@code KeyCodes.lwjglToSdlScancode(key)} でハードコードされたテーブルからスキャンコードを引く
 * 2. {@code SDL_GetKeyboardState()} の ByteBuffer でそのスキャンコードのインデックスを参照する
 *
 * <h3>根本原因</h3>
 * {@code lwjglToSdlScancode()} が未対応のキーに対して {@code SDL_SCANCODE_UNKNOWN(=0)} を返すと、
 * {@code sdlScancode <= 0} の条件チェックにより常に {@code false} が返される。
 * また日本語キーボード環境では、{@code populateKeyLookupTables()} でキー名テーブルが
 * SDL レイアウトに基づいて再構築されるが、この静的なスキャンコードテーブルは更新されないため、
 * キーコードとスキャンコードの対応が齟齬を起こす場合がある。
 *
 * <h3>修正方針</h3>
 * {@code lwjglToSdlScancode()} が {@code SDL_SCANCODE_UNKNOWN(=0)} を返した場合に、
 * lwjgl3ify の {@code KeyCodes.lwjglToSdlKeycode()} でキーコードを取得し、
 * SDL の {@code SDL_GetScancodeFromKey(keycode, null)} で動的にスキャンコードを解決する
 * フォールバック処理を追加する。
 */
@Mixin(targets = "org.lwjglx.input.Keyboard", remap = false)
public class Lwjgl3ifyKeyboardIsKeyDownMixin {

    /**
     * {@code isKeyDown(int key)} の戻り値を書き換える。
     *
     * 元の実装が {@code false} を返す場合（スキャンコードが見つからなかった等）に、
     * SDL の動的キーコード→スキャンコード変換を試みる。
     * 元が {@code true} の場合はそのまま通過させる（パフォーマンス優先）。
     */
    @Inject(
        method = "isKeyDown(I)Z",
        at = @At("RETURN"),
        remap = false,
        cancellable = true
    )
    private static void crosstie$fixKeyMapFallback(int key, CallbackInfoReturnable<Boolean> cir) {
        boolean originalResult = cir.getReturnValue();
        if (originalResult) {
            net.suzumiya.crosstie.compat.lwjgl3ify.Lwjgl3ifyKeyboardDebug.report(key, true, "source=lwjgl3ify");
            return;
        }

        if (key == 0) {
            net.suzumiya.crosstie.compat.lwjgl3ify.Lwjgl3ifyKeyboardDebug.report(key, false, "source=KEY_NONE");
            return;
        }

        ByteBuffer pressed = org.lwjglx.input.Keyboard.sdlKeyPressedArray;
        if (pressed == null) {
            net.suzumiya.crosstie.compat.lwjgl3ify.Lwjgl3ifyKeyboardDebug.report(key, false, "source=SDL-state-null");
            return;
        }

        int staticScancode = -1;
        boolean staticDown = false;
        int sdlKeycode = -1;
        int layoutScancode = -1;
        boolean layoutDown = false;
        try {
            staticScancode = org.lwjglx.input.KeyCodes.lwjglToSdlScancode(key);
            if (staticScancode > 0 && staticScancode < pressed.limit()) {
                staticDown = pressed.get(staticScancode) != 0;
            }

            sdlKeycode = org.lwjglx.input.KeyCodes.lwjglToSdlKeycode(key);
            if (sdlKeycode != -1) {
                try {
                    Class<?> sdlKeyboard = Class.forName("org.lwjgl.sdl.SDLKeyboard");
                    java.lang.reflect.Method getScancode = sdlKeyboard.getMethod("SDL_GetScancodeFromKey", int.class, java.nio.ShortBuffer.class);
                    layoutScancode = (Integer) getScancode.invoke(null, sdlKeycode, null);
                } catch (Throwable t) {
                    try {
                        Class<?> sdlKeyboardX = Class.forName("org.lwjglx.sdl.SDLKeyboard");
                        java.lang.reflect.Method getScancode = sdlKeyboardX.getMethod("SDL_GetScancodeFromKey", int.class, java.nio.ShortBuffer.class);
                        layoutScancode = (Integer) getScancode.invoke(null, sdlKeycode, null);
                    } catch (Throwable t2) {
                        // ignore
                    }
                }

                if (layoutScancode > 0 && layoutScancode < pressed.limit()) {
                    layoutDown = pressed.get(layoutScancode) != 0;
                }
            }

            boolean result = staticDown || layoutDown;
            if (result) {
                cir.setReturnValue(true);
            }
            net.suzumiya.crosstie.compat.lwjgl3ify.Lwjgl3ifyKeyboardDebug.report(
                key,
                result,
                "source=" + (staticDown ? "static-scancode" : layoutDown ? "layout-scancode" : "none")
                    + " staticScan=" + staticScancode
                    + " staticDown=" + staticDown
                    + " sdlKey=" + sdlKeycode
                    + " layoutScan=" + layoutScancode
                    + " layoutDown=" + layoutDown
            );
        } catch (Throwable error) {
            net.suzumiya.crosstie.compat.lwjgl3ify.Lwjgl3ifyKeyboardDebug.report(
                key,
                false,
                "source=exception type=" + error.getClass().getSimpleName()
                    + " message=" + String.valueOf(error.getMessage())
                    + " staticScan=" + staticScancode
                    + " staticDown=" + staticDown
                    + " sdlKey=" + sdlKeycode
                    + " layoutScan=" + layoutScancode
                    + " layoutDown=" + layoutDown
            );
        }
    }
}
