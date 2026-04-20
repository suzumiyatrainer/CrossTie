package net.suzumiya.crosstie.mixins.ngtlib;

import net.suzumiya.crosstie.util.McteMiniatureRenderContext;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NGTZ models use the same NGTO display-list compile path through their inner parts renderer.
 */
@Mixin(targets = "jp.ngt.ngtlib.renderer.model.NGTZModel$NGTOParts", remap = false)
public abstract class NGTZModelPartsMixin {

    @Inject(method = "renderBlocks", at = @At("HEAD"), remap = false)
    private void crosstie$prepareNgtzRender(CallbackInfo ci) {
        McteMiniatureRenderContext.enter();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }

    @Inject(method = "renderBlocks", at = @At("RETURN"), remap = false)
    private void crosstie$cleanupNgtzRender(CallbackInfo ci) {
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        McteMiniatureRenderContext.exit();
    }
}
