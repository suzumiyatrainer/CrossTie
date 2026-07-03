package net.suzumiya.crosstie.mixins.ngtlib;

import jp.ngt.rtm.gui.InternalGUI;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RTMの1.12.2マーカーUI（{@link InternalGUI}）のマウスピッキングパスにおいて、
 * AngelicaやOptiFineなどのシェーダーMODはGL_SELECTモードを無視して画面に描画してしまい、
 * 巨大な緑色の枠が表示されたりUIが濃くなったりする問題が発生する。
 *
 * 【修正内容】
 * ピッキングフェーズ中はカラーバッファと深度バッファへの書き込みをマスクし、
 * 描画パイプライン自体は通常通り実行させることで、当たり判定の座標を狂わせることなく
 * 画面への描画だけを抑制する。
 */
@Mixin(value = InternalGUI.class, remap = false)
public class GLHelperMousePickingFixMixin {

    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Ljp/ngt/ngtlib/renderer/GLHelper;startMousePicking(F)V",
            remap = false
        )
    )
    private void beforeStartMousePicking(CallbackInfo ci) {
        GL11.glColorMask(false, false, false, false);
        GL11.glDepthMask(false);
    }

    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Ljp/ngt/ngtlib/renderer/GLHelper;finishMousePicking()I",
            shift = At.Shift.AFTER,
            remap = false
        )
    )
    private void afterFinishMousePicking(CallbackInfo ci) {
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(true);
    }
}
