package net.suzumiya.crosstie.mixins.optifine;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "jp.ngt.rtm.render.WirePartsRenderer", remap = false)
public class WireColorShaderFixMixin {

    @Inject(method = "renderWireStraight", at = @At("HEAD"), remap = false, require = 0)
    private void crosstie$fixWireColor(CallbackInfo ci) {
        if (net.suzumiya.crosstie.CrossTieConfig.fixOptiFineWireNormalize) {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
