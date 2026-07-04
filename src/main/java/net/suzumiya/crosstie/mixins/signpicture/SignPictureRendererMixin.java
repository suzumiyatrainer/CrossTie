package net.suzumiya.crosstie.mixins.signpicture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SignPictureのレンダラー最適化用Mixin
 * - 目的: 画像描画時の不要なGLステート変更の削減や、キャッシュの効率化。
 */
@Mixin(targets = "com.kamesuta.mc.signpic.render.CustomTileEntitySignRenderer", remap = false)
public class SignPictureRendererMixin {

    @Inject(method = "renderTileEntityAt", at = @At("HEAD"), cancellable = true)
    public void onRenderTileEntityAt(Object tileEntity, double x, double y, double z, float partialTicks, CallbackInfo ci) {
        // TODO: 画像がロードされていない場合や、視界外（極端に遠い等）の
        // 無駄な描画コールをスキップするロジックを追加。
    }
}
