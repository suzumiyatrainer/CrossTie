package net.suzumiya.crosstie.mixins.ngtlib;

import net.suzumiya.crosstie.util.McteMiniatureRenderContext;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Extends the legacy display-list bypass to NGTO block-model rendering paths.
 */
@Mixin(targets = "jp.ngt.ngtlib.renderer.model.NGTOModel", remap = false)
public abstract class NGTOModelMixin {

    @Inject(method = "renderBlocks", at = @At("HEAD"), remap = false)
    private void crosstie$prepareNgtoRender(CallbackInfo ci) {
        McteMiniatureRenderContext.enter();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }

    @Inject(method = "renderBlocks", at = @At("RETURN"), remap = false)
    private void crosstie$cleanupNgtoRender(CallbackInfo ci) {
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        McteMiniatureRenderContext.exit();
    }
}
