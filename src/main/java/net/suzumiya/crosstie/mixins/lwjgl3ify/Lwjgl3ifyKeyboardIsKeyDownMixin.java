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
        // 元の実装がすでに true を返している場合はそのまま通す
        if (cir.getReturnValue()) {
            return;
        }

        // KEY_NONE(0) は無条件で false のまま
        if (key == 0) {
            return;
        }

        // sdlKeyPressedArray が null の場合はスキップ
        ByteBuffer array = org.lwjglx.input.Keyboard.sdlKeyPressedArray;
        if (array == null) {
            return;
        }

        try {
            // lwjglToSdlScancode が SDL_SCANCODE_UNKNOWN(0) を返した場合のフォールバック:
            // lwjglToSdlKeycode でキーコードを取得し、SDL から動的にスキャンコードを解決する
            int sdlScancode = org.lwjglx.input.KeyCodes.lwjglToSdlScancode(key);

            if (sdlScancode > 0 && sdlScancode < array.limit()) {
                // 元の実装と同じパスだが既に false が返っている（内容が 0）→ そのまま
                return;
            }

            // SDL_SCANCODE_UNKNOWN または範囲外 → キーコード経由でスキャンコードを動的解決
            int sdlKeycode = org.lwjglx.input.KeyCodes.lwjglToSdlKeycode(key);
            if (sdlKeycode == org.lwjgl.sdl.SDLKeycode.SDLK_UNKNOWN || sdlKeycode == -1) {
                return;
            }

            // SDL_GetScancodeFromKey(keycode, mods=null) で現在のキーボードレイアウトに基づく
            // スキャンコードを動的に取得する
            int resolvedScancode = org.lwjgl.sdl.SDLKeyboard.SDL_GetScancodeFromKey(sdlKeycode, (java.nio.ShortBuffer) null);

            if (resolvedScancode <= 0 || resolvedScancode >= array.limit()) {
                return;
            }

            if (array.get(resolvedScancode) != 0) {
                cir.setReturnValue(true);
            }
        } catch (Throwable t) {
            // 安全性のため例外はすべて握りつぶす（キー入力に失敗してもクラッシュしないように）
        }
    }
}
