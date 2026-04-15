package net.suzumiya.crosstie.mixins.rtm;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PartsRenderer のスクリプト実行前に GL コンテキストを整える。
 *
 * Angelica 環境でもスクリプトからの GL11 呼び出しが失敗しにくいようにする。
 */
@Mixin(targets = "jp.ngt.rtm.render.PartsRenderer", remap = false)
public class PartsRendererScriptContextMixin {

    private static final int MAX_GL_ERROR_DRAIN_ATTEMPTS = 16;

    /**
     * render() の開始時に GL コンテキストを準備する。
     */
    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void crosstie$prepareGLContextForRender(Object t, int pass, float partialTick, CallbackInfo ci) {
        ensureGLContextReady();
    }

    /**
     * スクリプト実行前に GL エラーをできる範囲で消す。
     */
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Ljp/ngt/rtm/render/PartsRenderer;execScriptFunc(Ljava/lang/String;[Ljava/lang/Object;)V", ordinal = 0), remap = false)
    private void crosstie$clearGLErrorsBeforeScriptExecution(Object t, int pass, float partialTick, CallbackInfo ci) {
        // エラーが残っている場合だけ、少数回だけ排出する
        for (int i = 0; i < MAX_GL_ERROR_DRAIN_ATTEMPTS; i++) {
            if (GL11.glGetError() == GL11.GL_NO_ERROR) {
                break;
            }
        }
    }

    /**
     * GL11 呼び出しに必要な状態を整える。
     */
    private static void ensureGLContextReady() {
        try {
            // 1. Angelica の GLStateManager があれば先に初期化する
            try {
                Class<?> glStateManagerClass = Class.forName(
                        "com.gtnewhorizons.angelica.glsm.GLStateManager",
                        false,
                        PartsRendererScriptContextMixin.class.getClassLoader());

                // コンテキストを有効にするため、glClientActiveTexture を呼ぶ
                glStateManagerClass.getDeclaredMethod("glClientActiveTexture", int.class)
                        .invoke(null, GL13.GL_TEXTURE0);
            } catch (ClassNotFoundException ignored) {
                // Angelica が無い場合はそのまま続ける
            } catch (ReflectiveOperationException e) {
                // 1 の方法が失敗したら、次の方法を試す
            }

            // 2. 代表的な GL 呼び出しで状態を確認する
            try {
                GL11.glPushMatrix();
                GL11.glPopMatrix();
            } catch (IllegalStateException e) {
                // GL コンテキストがまだ使えない場合があるが、ここでは止めない
            }

            // 3. 残っている GL エラーを消す
            for (int i = 0; i < MAX_GL_ERROR_DRAIN_ATTEMPTS; i++) {
                if (GL11.glGetError() == GL11.GL_NO_ERROR) {
                    break;
                }
            }
        } catch (Exception e) {
            // 失敗しても描画全体は止めない
        }
    }
}
