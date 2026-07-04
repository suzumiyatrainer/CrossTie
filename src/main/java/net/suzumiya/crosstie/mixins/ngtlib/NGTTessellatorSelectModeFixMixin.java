package net.suzumiya.crosstie.mixins.ngtlib;

import jp.ngt.ngtlib.renderer.NGTTessellator;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * AngelicaやOptiFine環境で、TessellatorがVBOやシェーダー描画に置き換わると、
 * レガシーなGL_SELECTモードでのマウスピッキングが動作しなくなる（ヒット0になる）問題がある。
 *
 * このMixinでは、描画モードがGL_SELECTである場合のみ、MinecraftのTessellatorへの
 * 委譲をキャンセルし、NGTTessellatorが自前で持っている固定機能の `drawVertexArray()` を
 * 強制的に使用させる。これにより、glDrawArraysを通して確実にGL_SELECTによる当たり判定を
 * 発生させることができる。
 */
@Mixin(value = NGTTessellator.class, remap = false)
public abstract class NGTTessellatorSelectModeFixMixin {

    @Shadow
    private boolean isDrawing;

    @Shadow
    private int drawVertexArray() {
        throw new AbstractMethodError("Shadow");
    }

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void injectDrawForSelectMode(CallbackInfoReturnable<Integer> cir) {
        if (GL11.glGetInteger(GL11.GL_RENDER_MODE) == GL11.GL_SELECT) {
            if (!this.isDrawing) {
                throw new IllegalStateException("Not tesselating!");
            }
            this.isDrawing = false;

            boolean hasVAO = org.lwjgl.opengl.GLContext.getCapabilities().OpenGL30;
            int currentVAO = 0;
            if (hasVAO) {
                currentVAO = GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_VERTEX_ARRAY_BINDING);
                org.lwjgl.opengl.GL30.glBindVertexArray(0);
            }

            boolean hasProgram = org.lwjgl.opengl.GLContext.getCapabilities().OpenGL20;
            int currentProgram = 0;
            if (hasProgram) {
                currentProgram = GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM);
                org.lwjgl.opengl.GL20.glUseProgram(0);
            }

            int ret = this.drawVertexArray();

            if (hasProgram) {
                org.lwjgl.opengl.GL20.glUseProgram(currentProgram);
            }
            if (hasVAO) {
                org.lwjgl.opengl.GL30.glBindVertexArray(currentVAO);
            }

            cir.setReturnValue(ret);
        }
    }
}
