package net.suzumiya.crosstie.mixins.ngtlib;

import net.suzumiya.crosstie.util.AngelicaCompatPolicy;
import net.suzumiya.crosstie.util.Hi03ExpressRailwayContext;
import net.suzumiya.crosstie.util.McteMiniatureRenderContext;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stabilize hi03 and MCTE miniature display-list compilation under Angelica.
 *
 * <p>hi03 rails: GL_COMPILE_AND_EXECUTE (隠し実行) を継続使用。<br>
 * MCTE miniature: display list compile を GL_COMPILE_AND_EXECUTE に昇格し、
 * Angelica の DisplayListManager バイパス済みネイティブ GL を使って即時描画する。
 * これにより「renderBlocks が private メソッドで @Inject できない」問題を回避しつつ、
 * ミニチュアを正常にレンダリングする。
 */
@Mixin(targets = "jp.ngt.ngtlib.renderer.GLHelper", remap = false)
public class GLHelperMixin {

    /**
     * [0] = hi03 hidden-compile フラグ
     * [1] = MCTE miniature compile-and-execute フラグ
     */
    @Unique
    private static final ThreadLocal<int[]> CROSSTIE_COMPILE_FLAGS =
            new ThreadLocal<int[]>() {
                @Override
                protected int[] initialValue() {
                    return new int[] { 0, 0 };
                }
            };

    @Redirect(method = "startCompile", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glNewList(II)V"), remap = false)
    private static void crosstie$interceptGlNewList(int list, int mode) {
        int[] flags = CROSSTIE_COMPILE_FLAGS.get();

        if (Hi03ExpressRailwayContext.isActive() && AngelicaCompatPolicy.shouldUseHi03LegacyDisplayLists()) {
            // hi03: hidden compile-and-execute (draw geometry but mask color+depth writes)
            GL11.glColorMask(false, false, false, false);
            GL11.glDepthMask(false);
            flags[0] = 1;
            GL11.glNewList(list, GL11.GL_COMPILE_AND_EXECUTE);
            return;
        }

        if (McteMiniatureRenderContext.isActive()) {
            // MCTE miniature: promote to GL_COMPILE_AND_EXECUTE so geometry is drawn immediately.
            // Angelica's DisplayListManager is already bypassed (see AngelicaDisplayListManagerMixin),
            // so glNewList here goes to native GL. The execute-pass renders the miniature correctly
            // without relying on @Inject into private renderBlocks.
            flags[1] = 1;
            GL11.glNewList(list, GL11.GL_COMPILE_AND_EXECUTE);
            return;
        }

        flags[0] = 0;
        flags[1] = 0;
        GL11.glNewList(list, mode);
    }

    @Inject(method = "endCompile", at = @At("RETURN"), remap = false)
    private static void crosstie$restoreAfterCompile(CallbackInfo ci) {
        int[] flags = CROSSTIE_COMPILE_FLAGS.get();

        if (flags[0] != 0) {
            // Restore hi03 hidden-compile masks
            GL11.glDepthMask(true);
            GL11.glColorMask(true, true, true, true);
            flags[0] = 0;
        }
        // MCTE miniature: no extra work needed; flags reset for next call
        flags[1] = 0;
    }
}
