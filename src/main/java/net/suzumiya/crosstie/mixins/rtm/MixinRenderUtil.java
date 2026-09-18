package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.util.RenderUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = RenderUtil.class, remap = false)
public class MixinRenderUtil {

    /**
     * @author Antigravity
     * @reason RTM uses GL_LIGHT4 to GL_LIGHT7 for custom lighting, which causes spam warnings under Angelica/Iris since FFP shader emulation only supports GL_LIGHT0 and GL_LIGHT1. Disabling custom lighting fallback to vanilla lighting which works correctly with Angelica/Iris.
     */
    @Overwrite
    private static int getLight(int id) {
        return -1;
    }
}
