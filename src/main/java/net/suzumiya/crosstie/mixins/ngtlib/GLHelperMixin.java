package net.suzumiya.crosstie.mixins.ngtlib;

import net.suzumiya.crosstie.util.Hi03ExpressRailwayContext;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stabilize hi03 rail display-list compilation under Angelica.
 *
 * We keep using GL_COMPILE_AND_EXECUTE for hi03 rails, but hide the execute pass so
 * temporary geometry does not leak into the main scene as visual artifacts.
 */
@Mixin(targets = "jp.ngt.ngtlib.renderer.GLHelper", remap = false)
public class GLHelperMixin {

    @Unique
    private static final ThreadLocal<int[]> CROSSTIE_HIDDEN_COMPILE =
            new ThreadLocal<int[]>() {
                @Override
                protected int[] initialValue() {
                    return new int[] { 0 };
                }
            };

    @Redirect(method = "startCompile", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glNewList(II)V"), remap = false)
    private static void crosstie$compileAndExecuteForHi03(int list, int mode) {
        int[] hidden = CROSSTIE_HIDDEN_COMPILE.get();
        if (Hi03ExpressRailwayContext.isActive()) {
            GL11.glColorMask(false, false, false, false);
            GL11.glDepthMask(false);
            hidden[0] = 1;
            GL11.glNewList(list, GL11.GL_COMPILE_AND_EXECUTE);
            return;
        }

        hidden[0] = 0;
        GL11.glNewList(list, mode);
    }

    @Inject(method = "endCompile", at = @At("RETURN"), remap = false)
    private static void crosstie$restoreAfterHi03Compile(CallbackInfo ci) {
        int[] hidden = CROSSTIE_HIDDEN_COMPILE.get();
        if (hidden[0] == 0) {
            return;
        }

        GL11.glDepthMask(true);
        GL11.glColorMask(true, true, true, true);
        hidden[0] = 0;
    }
}
