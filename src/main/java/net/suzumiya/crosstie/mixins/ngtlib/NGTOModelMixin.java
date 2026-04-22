package net.suzumiya.crosstie.mixins.ngtlib;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Extends the legacy display-list bypass to NGTO block-model rendering paths.
 *
 * <p>McteMiniatureRenderContext の enter/exit は、上位の RenderMiniature/RenderItemMiniature
 * の renderTileEntityAt/renderItem レベルで管理するため、ここでは行わない。
 * ネストによるカウンタ崩れを防ぐため、GL 行列モードの保証のみを行う。
 */
@Mixin(targets = "jp.ngt.ngtlib.renderer.model.NGTOModel", remap = false)
public abstract class NGTOModelMixin {

    @Inject(method = "renderBlocks", at = @At("HEAD"), remap = false)
    private void crosstie$ensureModelviewModeHead(CallbackInfo ci) {
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }

    @Inject(method = "renderBlocks", at = @At("RETURN"), remap = false)
    private void crosstie$ensureModelviewModeReturn(CallbackInfo ci) {
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }
}
