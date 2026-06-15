package net.suzumiya.crosstie.mixins.liteloader;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * LiteLoader (Macro / Keybind) が 60 秒以上 tick されないと
 * IllegalStateException (tamperCheck) を投げてクラッシュします。
 *
 * コンパイル時に LiteLoader のクラスが無くてもビルドできるように
 * {@code targets} でターゲットクラスを文字列指定します。
 */
@Mixin(targets = "com.mumfrey.liteloader.permissions.PermissionsManagerClient", remap = false)
public class MixinPermissionsManagerClient {

    /** tamperCheck が呼ばれたら例外を出さずに処理を中断 */
    @Inject(method = "tamperCheck", at = @At("HEAD"), cancellable = true)
    private void crosstie$disableTamperCheck(CallbackInfo ci) {
        ci.cancel(); // 例外送出を防ぎます
    }
}