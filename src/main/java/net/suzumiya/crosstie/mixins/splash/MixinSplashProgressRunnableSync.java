package net.suzumiya.crosstie.mixins.splash;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "cpw.mods.fml.client.SplashProgress$3", remap = false)
public class MixinSplashProgressRunnableSync {

    // 初期化済みフラグ
    private static boolean initialized = false;

    @Inject(method = "run", at = @At("HEAD"))
    private void injectInitGLState(CallbackInfo ci) {
        // BackendManagerの初期化のみ行う（OpenGL操作は行わない）
        if (!initialized) {
            if (com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND == null) {
                com.gtnewhorizons.angelica.glsm.backend.BackendManager.init();
            }
            initialized = true;
        }
    }

    // --- Angelicaの記録対象にするためのリダイレクト群 ---

    @Redirect(method = "run", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glPushMatrix()V"))
    private void redirectPushMatrix() {
        GLStateManager.glPushMatrix();
    }

    @Redirect(method = "run", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glPopMatrix()V"))
    private void redirectPopMatrix() {
        GLStateManager.glPopMatrix();
    }

    @Redirect(method = "run", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEnable(I)V"))
    private void redirectEnable(int cap) {
        GLStateManager.glEnable(cap);
    }

    @Redirect(method = "run", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V"))
    private void redirectDisable(int cap) {
        GLStateManager.glDisable(cap);
    }

    @Redirect(method = "run", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColor4f(FFFF)V"))
    private void redirectColor(float r, float g, float b, float a) {
        GLStateManager.glColor4f(r, g, b, a);
    }

    // --- 重要: 頂点描画の記録をトリガーする ---
    @Redirect(method = "run", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glBegin(I)V"))
    private void redirectBegin(int mode) {
        // Angelicaのテッセレータを介して記録を開始させる
        GLStateManager.glBegin(mode);
    }

    @Redirect(method = "run", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEnd()V"))
    private void redirectEnd() {
        GLStateManager.glEnd();
    }

    @Redirect(method = "run", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glClear(I)V"))
    private void redirectClear(int mask) {
        // FMLによるクリア命令が飛んだら、確実にメインコンテキストへ通知する
        GLStateManager.glClear(mask);
    }

    // デバッグログ
    @Redirect(method = "run", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glBegin(I)V"))
    private void debugGlBegin(int mode) {
        System.out.println("[CrossTie-Debug] glBegin called: " + mode);
        GLStateManager.glBegin(mode);
    }
}