package net.suzumiya.crosstie.mixins.ngtlib;

import jp.ngt.rtm.rail.RenderMarkerBlock1122;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.suzumiya.crosstie.util.TrueGL;

/**
 * Angelica/GLSM によって GL11.glLoadName が無効化されているため、
 * リフレクション(TrueGL)経由で呼び出すようにリダイレクトする。
 */
@Mixin(value = RenderMarkerBlock1122.class, remap = false)
public class RenderMarkerBlock1122LoadNameMixin {

    @Redirect(
        method = "renderAnchorLine",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glLoadName(I)V",
            remap = false
        )
    )
    private void redirectGlLoadName(int name) {
        TrueGL.glLoadName(name);
    }
}
