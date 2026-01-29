package net.suzumiya.crosstie.mixins.ngtlib;

import net.suzumiya.crosstie.util.Hi03ExpressRailwayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PolygonRendererへのMixin。
 * 
 * hi03ExpressRailwayコンテキストがアクティブな場合、
 * Angelicaのディスプレイリスト最適化（VBO変換）をバイパスして
 * 直接OpenGL呼び出しを使用します。
 * 
 * これにより、DisplayListコンパイル内でPolygonRendererを使用する
 * hi03ExpressRailwayのバラスト描画問題を修正します。
 */
@Mixin(targets = "jp.ngt.ngtlib.renderer.PolygonRenderer", remap = false)
public abstract class PolygonRendererMixin {

    /**
     * startDrawing時にhi03コンテキストならglBeginをバイパスマーク
     * 
     * Note: Angelicaはglsm経由でGL11.glBegin()をインターセプトするため、
     * この時点でコンテキストフラグを確認し、必要に応じてバイパスロジックを適用
     */
    @Inject(method = "startDrawing", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$bypassAngelicaStartDrawing(int mode, CallbackInfo ci) {
        if (Hi03ExpressRailwayContext.isActive()) {
            // Angelicaのインターセプトをバイパスして直接LWJGL GL11を呼び出し
            // org.lwjgl.opengl.GL11 を直接使用することで、
            // AngelicaのGLStateManagerフックを回避
            org.lwjgl.opengl.GL11.glBegin(mode);
            ci.cancel();
        }
    }

    /**
     * draw時にhi03コンテキストなら直接glEndを呼び出し
     */
    @Inject(method = "draw", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$bypassAngelicaDraw(CallbackInfoReturnable<Integer> cir) {
        if (Hi03ExpressRailwayContext.isActive()) {
            // Angelicaのインターセプトをバイパスして直接LWJGL GL11を呼び出し
            org.lwjgl.opengl.GL11.glEnd();
            cir.setReturnValue(0);
        }
    }

    /**
     * addVertexWithUV時にhi03コンテキストなら直接GL実行
     */
    @Inject(method = "addVertexWithUV", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$bypassAngelicaVertex(float x, float y, float z, float u, float v, CallbackInfo ci) {
        if (Hi03ExpressRailwayContext.isActive()) {
            org.lwjgl.opengl.GL11.glTexCoord2f(u, v);
            org.lwjgl.opengl.GL11.glVertex3f(x, y, z);
            ci.cancel();
        }
    }

    /**
     * setNormal時にhi03コンテキストなら直接GL実行
     */
    @Inject(method = "setNormal", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$bypassAngelicaNormal(float x, float y, float z, CallbackInfo ci) {
        if (Hi03ExpressRailwayContext.isActive()) {
            org.lwjgl.opengl.GL11.glNormal3f(x, y, z);
            ci.cancel();
        }
    }
}
