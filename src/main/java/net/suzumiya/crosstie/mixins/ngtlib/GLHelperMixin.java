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
    private static final ThreadLocal<Boolean> CROSSTIE_HIDDEN_COMPILE = ThreadLocal.withInitial(() -> false);

    @Redirect(method = "startCompile", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glNewList(II)V"), remap = false)
    private static void crosstie$compileAndExecuteForHi03(int list, int mode) {
        if (Hi03ExpressRailwayContext.isActive()) {
            GL11.glColorMask(false, false, false, false);
            GL11.glDepthMask(false);
            CROSSTIE_HIDDEN_COMPILE.set(true);
            GL11.glNewList(list, GL11.GL_COMPILE_AND_EXECUTE);
            return;
        }

        CROSSTIE_HIDDEN_COMPILE.remove();
        GL11.glNewList(list, mode);
    }

    @Inject(method = "endCompile", at = @At("RETURN"), remap = false)
    private static void crosstie$restoreAfterHi03Compile(CallbackInfo ci) {
        if (!CROSSTIE_HIDDEN_COMPILE.get()) {
            return;
        }

        GL11.glDepthMask(true);
        GL11.glColorMask(true, true, true, true);
        CROSSTIE_HIDDEN_COMPILE.remove();
    }
}
