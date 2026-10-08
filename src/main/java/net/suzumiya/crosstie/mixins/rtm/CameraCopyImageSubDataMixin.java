package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.gui.camera.Camera;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * RTMの Camera.copyScreenBuf() で使われる GL43.glCopyImageSubData() を、
 * AngelicaのGLSM（OpenGLステートマシン）がマッピングしていないため、 GL
 * 3.0のFBOブリット（glBlitFramebuffer）で代替する。
 *
 * GL43.glCopyImageSubData がGLSMのリダイレクタに到達するとGLステートが破損し、
 * ワールドを抜けた後のメインメニューが真っ黒になる問題を修正する。
 */
@Mixin(value = Camera.class, remap = false)
public class CameraCopyImageSubDataMixin {

    @Shadow
    private int[] mcScreenTex;

    @Shadow
    private boolean mcTexRendered;

    @Shadow
    private float frameBufU;

    @Shadow
    private float frameBufV;

    /**
     * GL43.glCopyImageSubData() の呼び出しをFBOブリットに差し替える。
     *
     * 元のコード: GL43.glCopyImageSubData(srcTex, GL_TEXTURE_2D, 0, 0,0,0, dstTex,
     * GL_TEXTURE_2D, 0, 0,0,0, w, h, 1);
     *
     * 代替: srcTexをFBOにアタッチしてdstTexへBlitする（GL 3.0、GLSMが処理可能）。
     */
    @Redirect(method = "copyScreenBuf", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL43;glCopyImageSubData(IIIIIIIIIIIIIII)V"))
    private void crosstie$replaceCopyImageSubData(int srcName, int srcTarget, int srcLevel, int srcX, int srcY,
            int srcZ, int dstName, int dstTarget, int dstLevel, int dstX, int dstY, int dstZ, int srcWidth,
            int srcHeight, int srcDepth) {

        // 読み取り元FBOを生成してsrcTexをアタッチ
        int readFbo = GL30.glGenFramebuffers();
        int drawFbo = GL30.glGenFramebuffers();
        try {
            // 読み取り元: srcNameテクスチャをFBOにアタッチ
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D,
                    srcName, srcLevel);

            // 書き込み先: dstNameテクスチャをFBOにアタッチ
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
            GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D,
                    dstName, dstLevel);

            // ブリット（テクスチャ間コピー）
            GL30.glBlitFramebuffer(srcX, srcY, srcX + srcWidth, srcY + srcHeight, dstX, dstY, dstX + srcWidth,
                    dstY + srcHeight, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        } finally {
            // FBOをアンバインドして削除（GLステートを復元）
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
            GL30.glDeleteFramebuffers(readFbo);
            GL30.glDeleteFramebuffers(drawFbo);
        }
    }
}
