package net.suzumiya.crosstie.mixins.ngtlib;

import jp.ngt.ngtlib.renderer.NGTTessellator;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.suzumiya.crosstie.util.TrueGL;

/**
 * AngelicaやOptiFine環境で、TessellatorがVBOやシェーダー描画に置き換わると、
 * 3D空間上の矢印や線などのGL_SELECTによるマウスピッキングが動作しなくなる問題。
 *
 * 【修正内容】
 * GL_SELECTモード中のみ、AngelicaのVBOやシェーダーを一時的に無効化し、
 * 完全な互換モードの即時描画（glBegin/glEnd）で頂点を送り出す。
 */
@Mixin(value = NGTTessellator.class, remap = false)
public abstract class NGTTessellatorSelectModeFixMixin {

    @Shadow private boolean isDrawing;
    @Shadow private int drawMode;
    @Shadow private int vertexCount;
    @Shadow private int[] rawBuffer;
    @Shadow private int rawBufferIndex;
    @Shadow private int rawBufferSize;

    @Shadow
    private void reset() {
        throw new AbstractMethodError("Shadow");
    }

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void injectDrawForSelectMode(CallbackInfoReturnable<Integer> cir) {
        if (TrueGL.isSelectMode()) {
            if (!this.isDrawing) {
                throw new IllegalStateException("Not tesselating!");
            }
            this.isDrawing = false;
            System.out.println("[CrossTie] NGTTessellator draw intercepted in SELECT mode! Vertices: " + this.vertexCount + ", Mode: " + this.drawMode);

            boolean hasVAO = org.lwjgl.opengl.GLContext.getCapabilities().OpenGL30;
            boolean hasVBO = org.lwjgl.opengl.GLContext.getCapabilities().OpenGL15;
            boolean hasProgram = org.lwjgl.opengl.GLContext.getCapabilities().OpenGL20;

            int currentVAO = 0;
            int currentVBO = 0;
            int currentEBO = 0;
            int currentProgram = 0;

            if (hasVAO) {
                currentVAO = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
                GL30.glBindVertexArray(0);
            }
            if (hasVBO) {
                currentVBO = GL11.glGetInteger(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER_BINDING);
                currentEBO = GL11.glGetInteger(org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
                org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, 0);
                org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
            }
            if (hasProgram) {
                currentProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
                GL20.glUseProgram(0);
            }

            if (this.vertexCount > 0) {
                // Angelicaのソフトウェア行列スタックをドライバに同期させる
                TrueGL.syncMatricesToDriver();
                
                GL11.glBegin(this.drawMode);
                for (int i = 0; i < this.vertexCount; i++) {
                    int index = i * 8;
                    float x = Float.intBitsToFloat(this.rawBuffer[index]);
                    float y = Float.intBitsToFloat(this.rawBuffer[index + 1]);
                    float z = Float.intBitsToFloat(this.rawBuffer[index + 2]);
                    GL11.glVertex3f(x, y, z);
                }
                GL11.glEnd();
            }

            int result = this.rawBufferIndex;

            if (this.rawBufferSize > 0x20000 && this.rawBufferIndex < (this.rawBufferSize << 3)) {
                this.rawBufferSize = 0x10000;
                this.rawBuffer = new int[this.rawBufferSize];
            }

            this.reset();

            // 復元処理：VBOを先に復元してからVAOを復元する
            if (hasVBO) {
                org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, currentVBO);
                org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER, currentEBO);
            }
            if (hasVAO) {
                GL30.glBindVertexArray(currentVAO);
            }
            if (hasProgram) {
                GL20.glUseProgram(currentProgram);
            }

            cir.setReturnValue(result);
        }
    }
}
