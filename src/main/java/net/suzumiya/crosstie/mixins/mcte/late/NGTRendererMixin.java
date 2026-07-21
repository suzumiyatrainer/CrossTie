package net.suzumiya.crosstie.mixins.mcte.late;

import jp.ngt.ngtlib.renderer.NGTRenderer;
import net.suzumiya.crosstie.util.NGTRenderState;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Angelica環境下で、NGTObjectの描画中にGL_LIGHTINGを無効化するMixin。
 *
 * <p>
 * AngelicaはOpenGL固定機能パイプライン（GL_LIGHTING, glLight, glMaterial, glNormal等）を
 * GLSLシェーダーへ自動変換して再現する（FFPエミュレーション）実装を持っている。
 * バニラのRenderHelper.enableStandardItemLighting()系が設定する擬似ディレクショナルライトが
 * 法線と内積計算され、カメラ・ワールド方向に応じて明るさが変動する現象の原因となっていた。
 * NGTObject描画区間中はGL_LIGHTINGを完全に無効化し、明るさをlightmap（brightness）値のみで
 * 決定させることで、方角依存の陰影計算を排除する。
 */
@Mixin(value = NGTRenderer.class, remap = false)
public class NGTRendererMixin {

    @Inject(method = "renderNGTObject(Ljp/ngt/ngtlib/world/IBlockAccessNGT;Ljp/ngt/ngtlib/renderer/NGTObject;ZII)V", at = @At("HEAD"))
    private static void crosstie$onRenderNGTObjectStart(CallbackInfo ci) {
        NGTRenderState.pushRendering();

        // GL_LIGHTING状態を保存し、NGT描画区間中は無効化する。
        // これにより法線×光源方向の内積によるdiffuse項計算がスキップされ、
        // 明るさはlightmap（brightness）値だけで決まるようになる。
        GL11.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GL11.glDisable(GL11.GL_LIGHTING);
    }

    @Inject(method = "renderNGTObject(Ljp/ngt/ngtlib/world/IBlockAccessNGT;Ljp/ngt/ngtlib/renderer/NGTObject;ZII)V", at = @At("RETURN"))
    private static void crosstie$onRenderNGTObjectEnd(CallbackInfo ci) {
        // GL_LIGHTING状態を元に戻す
        GL11.glPopAttrib();

        NGTRenderState.popRendering();
    }
}