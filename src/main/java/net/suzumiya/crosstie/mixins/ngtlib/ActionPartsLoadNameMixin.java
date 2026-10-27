package net.suzumiya.crosstie.mixins.ngtlib;

import jp.ngt.rtm.render.ActionParts;
import jp.ngt.rtm.render.PartsRenderer;
import jp.ngt.rtm.render.RenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Angelica/GLSM によって GL11.glLoadName が無効化されているため、 リフレクション(TrueGL)経由で呼び出すようにする。
 */
@Mixin(value = ActionParts.class, remap = false)
public class ActionPartsLoadNameMixin {

    @Shadow
    public int id;

    @SuppressWarnings("rawtypes")
    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void crosstie$onRenderHead(PartsRenderer renderer, CallbackInfo ci) {
        if (renderer.currentPass == RenderPass.PICK.id) {
            net.suzumiya.crosstie.utils.ActionPartsMathPicker.setCurrentPartId(this.id);
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glLoadName(I)V", remap = false), require = 0)
    private void redirectGlLoadName(int name) {
        net.suzumiya.crosstie.utils.ActionPartsMathPicker.setCurrentPartId(name);
    }
}
