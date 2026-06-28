package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.render.Parts;
import jp.ngt.rtm.render.PartsRenderer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Parts.class, remap = false)
public class RtmPartsMatrixPushMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void crosstie$onRenderHead(PartsRenderer<?, ?> renderer, CallbackInfo ci) {
        GL11.glPushMatrix();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void crosstie$onRenderReturn(PartsRenderer<?, ?> renderer, CallbackInfo ci) {
        GL11.glPopMatrix();
    }
}
