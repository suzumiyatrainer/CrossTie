package net.suzumiya.crosstie.mixins.angelica;

import jp.ngt.ngtlib.renderer.PolygonRenderer;
import jp.ngt.ngtlib.renderer.NGTTessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PolygonRenderer.class, remap = false)
public class PolygonRendererAngelicaMixin {

    @Inject(method = "startDrawing", at = @At("HEAD"), cancellable = true)
    private void crosstie$onStartDrawing(int mode, CallbackInfo ci) {
        NGTTessellator.instance.startDrawing(mode);
        ci.cancel();
    }

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void crosstie$onDraw(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(NGTTessellator.instance.draw());
    }

    @Inject(method = "addVertexWithUV", at = @At("HEAD"), cancellable = true)
    private void crosstie$onAddVertexWithUV(float x, float y, float z, float u, float v, CallbackInfo ci) {
        NGTTessellator.instance.addVertexWithUV(x, y, z, u, v);
        ci.cancel();
    }

    @Inject(method = "setNormal", at = @At("HEAD"), cancellable = true)
    private void crosstie$onSetNormal(float x, float y, float z, CallbackInfo ci) {
        NGTTessellator.instance.setNormal(x, y, z);
        ci.cancel();
    }

    @Inject(method = "setBrightness", at = @At("HEAD"), cancellable = true)
    private void crosstie$onSetBrightness(int par1, CallbackInfo ci) {
        NGTTessellator.instance.setBrightness(par1);
        ci.cancel();
    }
}
