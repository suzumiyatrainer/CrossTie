package net.suzumiya.crosstie.mixins.ngtlib;

import net.suzumiya.crosstie.util.Hi03ExpressRailwayContext;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * GLHelperへのMixin。
 * 
 * hi03ExpressRailwayコンテキストがアクティブな場合、
 * GL_COMPILEをGL_COMPILE_AND_EXECUTEに変更して、
 * ディスプレイリストコンパイル中も直接描画を行います。
 * これによりAngelicaのVBO変換後のcallListが失敗しても描画が維持されます。
 */
@Mixin(targets = "jp.ngt.ngtlib.renderer.GLHelper", remap = false)
public class GLHelperMixin {

    /**
     * hi03ExpressRailwayコンテキストがアクティブな場合、
     * GL_COMPILE を GL_COMPILE_AND_EXECUTE に変更して即座に描画も実行
     */
    @Redirect(method = "startCompile", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glNewList(II)V"), remap = false)
    private static void crosstie$compileAndExecuteForHi03(int list, int mode) {
        if (Hi03ExpressRailwayContext.isActive()) {
            // hi03ExpressRailwayの場合、GL_COMPILE_AND_EXECUTEで
            // コンパイルと同時に描画も実行
            GL11.glNewList(list, GL11.GL_COMPILE_AND_EXECUTE);
            return;
        }
        // 通常パス - GL_COMPILE（キャプチャのみ）
        GL11.glNewList(list, mode);
    }
}
