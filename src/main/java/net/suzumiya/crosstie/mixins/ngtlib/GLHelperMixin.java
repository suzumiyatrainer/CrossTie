package net.suzumiya.crosstie.mixins.ngtlib;

import net.suzumiya.crosstie.util.Hi03ExpressRailwayContext;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * GLHelper に対する mixin。
 *
 * hi03ExpressRailway の描画中は GL_COMPILE を GL_COMPILE_AND_EXECUTE に置き換えて、
 * ディスプレイリストのコンパイル中でも即時描画を通します。これにより Angelica の
 * VBO 変換後に callList が失敗するのを防ぎます。
 */
@Mixin(targets = "jp.ngt.ngtlib.renderer.GLHelper", remap = false)
public class GLHelperMixin {

    /**
     * hi03ExpressRailway の描画中は、GL_COMPILE を GL_COMPILE_AND_EXECUTE に置き換える。
     */
    @Redirect(method = "startCompile", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glNewList(II)V"), remap = false)
    private static void crosstie$compileAndExecuteForHi03(int list, int mode) {
        if (Hi03ExpressRailwayContext.isActive()) {
            // hi03ExpressRailway の描画中は、コンパイルと描画を同時に行う
            GL11.glNewList(list, GL11.GL_COMPILE_AND_EXECUTE);
            return;
        }
        // 通常時はそのまま処理する
        GL11.glNewList(list, mode);
    }
}
