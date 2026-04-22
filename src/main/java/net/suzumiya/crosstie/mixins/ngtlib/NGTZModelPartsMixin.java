package net.suzumiya.crosstie.mixins.ngtlib;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NGTZ モデルのブロックパーツレンダリング時の GL 行列モード保証。
 *
 * <p>NGTOModel と同様、McteMiniatureRenderContext の enter/exit は
 * 上位レベル（RenderMiniature/RenderItemMiniature）で管理するため、
 * ここでは行列モードの保証のみを行い、ネストによるカウンタ崩れを防ぐ。
 */
@Mixin(targets = "jp.ngt.ngtlib.renderer.model.NGTZModel$NGTOParts", remap = false)
public abstract class NGTZModelPartsMixin {

    @Inject(method = "renderBlocks", at = @At("HEAD"), remap = false)
    private void crosstie$ensureModelviewModeHead(CallbackInfo ci) {
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }

    @Inject(method = "renderBlocks", at = @At("RETURN"), remap = false)
    private void crosstie$ensureModelviewModeReturn(CallbackInfo ci) {
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }
}
