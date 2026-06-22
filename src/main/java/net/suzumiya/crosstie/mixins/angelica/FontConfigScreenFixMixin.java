package net.suzumiya.crosstie.mixins.angelica;

import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Angelica の FontConfigScreen が {@code initGui()} で有効化した
 * {@link Keyboard#enableRepeatEvents(boolean)} を、画面終了時に確実に無効化する。
 *
 * <p>この Mixin がないと、FontConfigScreen を閉じた後もキーリピートイベントが
 * 有効なまま残り、E キー（インベントリキー）の長押し時にリピートイベントが
 * インベントリトグルを繰り返し発火させてしまう。</p>
 */
@Mixin(targets = "com.gtnewhorizons.angelica.client.gui.FontConfigScreen", remap = false)
public class FontConfigScreenFixMixin {

    /**
     * {@code onClose()} の先頭でキーリピートを無効化する。
     *
     * <p>Angelica の {@code FontConfigScreen.initGui()} は
     * {@code Keyboard.enableRepeatEvents(true)} を実行するが、
     * 対応する無効化処理を {@code onClose()} で行っていないため、
     * 画面を閉じた後もキーリピートが有効のままになる。</p>
     */
    @Inject(method = "onClose", at = @At("HEAD"))
    private void crosstie$disableRepeatEventsOnClose(CallbackInfo ci) {
        Keyboard.enableRepeatEvents(false);
    }
}