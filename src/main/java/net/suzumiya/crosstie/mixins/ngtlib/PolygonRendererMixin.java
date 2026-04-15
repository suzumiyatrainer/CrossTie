package net.suzumiya.crosstie.mixins.ngtlib;

import net.suzumiya.crosstie.util.Hi03ExpressRailwayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PolygonRenderer に対する mixin。
 *
 * hi03ExpressRailway の描画中は、Angelica の display list 変換を避けて OpenGL の
 * 直接描画経路を使います。これで VBO 変換後の描画崩れを抑えます。
 */
@Mixin(targets = "jp.ngt.ngtlib.renderer.PolygonRenderer", remap = false)
public abstract class PolygonRendererMixin {

    /**
     * startDrawing 時は hi03 コンテキストを優先して、Angelica の介入を避ける。
     */
    @Inject(method = "startDrawing", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$bypassAngelicaStartDrawing(int mode, CallbackInfo ci) {
        if (Hi03ExpressRailwayContext.isActive()) {
            // Angelica を経由せず、直接 LWJGL の GL11 を呼ぶ
            org.lwjgl.opengl.GL11.glBegin(mode);
            ci.cancel();
        }
    }

    /**
     * draw 時は hi03 コンテキストを優先して、Angelica の介入を避ける。
     */
    @Inject(method = "draw", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$bypassAngelicaDraw(CallbackInfoReturnable<Integer> cir) {
        if (Hi03ExpressRailwayContext.isActive()) {
            // Angelica を経由せず、直接 LWJGL の GL11 を呼ぶ
            org.lwjgl.opengl.GL11.glEnd();
            cir.setReturnValue(0);
        }
    }

    /**
     * addVertexWithUV 時は hi03 コンテキストを優先して、Angelica の介入を避ける。
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
     * setNormal 時は hi03 コンテキストを優先して、Angelica の介入を避ける。
     */
    @Inject(method = "setNormal", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$bypassAngelicaNormal(float x, float y, float z, CallbackInfo ci) {
        if (Hi03ExpressRailwayContext.isActive()) {
            org.lwjgl.opengl.GL11.glNormal3f(x, y, z);
            ci.cancel();
        }
    }
}
