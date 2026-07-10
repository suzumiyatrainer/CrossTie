package net.suzumiya.crosstie.mixins.ngtlib;

import jp.ngt.rtm.render.ActionParts;
import net.suzumiya.crosstie.utils.TrueGL;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Angelica/GLSM によって GL11.glLoadName が無効化されているため、
 * リフレクション(TrueGL)経由で呼び出すようにリダイレクトする。
 */
@Mixin(value = ActionParts.class, remap = false)
public class ActionPartsLoadNameMixin {

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glLoadName(I)V", remap = false))
    private void redirectGlLoadName(int name) {
        TrueGL.glLoadName(name);
    }
}
