package net.suzumiya.crosstie.mixins.ngtlib;

import jp.ngt.rtm.rail.RenderMarkerBlock1122;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.BufferUtils;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import jp.ngt.rtm.rail.TileEntityMarker;
import jp.ngt.rtm.rail.RenderMarkerBlock1122.MarkerElement;

@Mixin(value = RenderMarkerBlock1122.class, remap = false)
public class RenderMarkerBlock1122MathPickingMixin {

    private static int lastLoadedName = -1;
    private static int closestName = -1;
    private static float closestZ = Float.MAX_VALUE;

    private static FloatBuffer modelMatrix = BufferUtils.createFloatBuffer(16);
    private static FloatBuffer projMatrix = BufferUtils.createFloatBuffer(16);
    private static IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private static FloatBuffer winPos1 = BufferUtils.createFloatBuffer(3);
    private static FloatBuffer winPos2 = BufferUtils.createFloatBuffer(3);

    @Inject(method = "renderAnchorLine", at = @At("HEAD"))
    private void onRenderAnchorLineHead(TileEntityMarker marker, boolean isPickMode, MarkerElement hoveredElement, CallbackInfoReturnable<MarkerElement> cir) {
        if (isPickMode) {
            closestName = -1;
            closestZ = Float.MAX_VALUE;
            // 行列を取得 (AngelicaがGLStateManagerで隠蔽していても、ドライバ側に同期されている前提か、あるいはAngelicaから取得する必要がある)
            // しかし、AngelicaのglGetFloatを使えば確実！
            try {
                Class<?> glStateManagerClass = Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager");
                java.lang.reflect.Method glGetFloatMethod = glStateManagerClass.getMethod("glGetFloat", int.class, FloatBuffer.class);
                
                projMatrix.clear();
                glGetFloatMethod.invoke(null, GL11.GL_PROJECTION_MATRIX, projMatrix);
            } catch (Exception e) {
                // フォールバック: ネイティブから取得
                projMatrix.clear();
                GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projMatrix);
            }
            viewport.clear();
            GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        }
    }

    @Redirect(method = "renderAnchorLine", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glLoadName(I)V", remap = false))
    private void redirectGlLoadName(int name) {
        lastLoadedName = name;
        // 本来のglLoadNameは不要になるが、一応呼んでおく
        // GL11.glLoadName(name); // <-- 省略可能
    }

    @Inject(method = "renderLine", at = @At("HEAD"), cancellable = true)
    private void onRenderLine(float startX, float startY, float startZ, float endX, float endY, float endZ, int color, CallbackInfo ci) {
        // TrueGL.isSelectMode()等を使わず、closestNameが初期化されていればPickModeと判定
        // だが正確にはisPickModeフラグを知る必要がある。
        // 上記HEADインジェクトで isPickMode なら closestName を -1 にしているので、
        // renderAnchorLineの実行中かつisPickModeなら判定する。
        if (closestZ != -1.0f && closestName != -2) { // 便宜上のフラグとして扱う
            try {
                Class<?> glStateManagerClass = Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager");
                java.lang.reflect.Method glGetFloatMethod = glStateManagerClass.getMethod("glGetFloat", int.class, FloatBuffer.class);
                modelMatrix.clear();
                glGetFloatMethod.invoke(null, GL11.GL_MODELVIEW_MATRIX, modelMatrix);
            } catch (Exception e) {
                modelMatrix.clear();
                GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelMatrix);
            }
            
            // 数学的ピッキング
            winPos1.clear();
            winPos2.clear();
            GLU.gluProject(startX, startY, startZ, modelMatrix, projMatrix, viewport, winPos1);
            GLU.gluProject(endX, endY, endZ, modelMatrix, projMatrix, viewport, winPos2);

            float px1 = winPos1.get(0);
            float py1 = winPos1.get(1);
            float pz1 = winPos1.get(2);

            float px2 = winPos2.get(0);
            float py2 = winPos2.get(1);
            float pz2 = winPos2.get(2);

            float mouseX = (float) org.lwjgl.opengl.Display.getWidth() / 2.0F;
            float mouseY = (float) org.lwjgl.opengl.Display.getHeight() / 2.0F;

            // Z値のチェック: 画面より後ろのものは除外 (0.0=Near, 1.0=Far)
            // 少し余裕をもたせて、負のZでも近すぎればOKとするが、基本は0~1
            if ((pz1 >= -0.1f && pz1 <= 1.1f) || (pz2 >= -0.1f && pz2 <= 1.1f)) {
                // 線分 (px1, py1) - (px2, py2) と点 (mouseX, mouseY) の最短距離を求める
                float l2 = (px2 - px1) * (px2 - px1) + (py2 - py1) * (py2 - py1);
                float t = 0;
                if (l2 > 0.0001f) {
                    t = Math.max(0, Math.min(1, ((mouseX - px1) * (px2 - px1) + (mouseY - py1) * (py2 - py1)) / l2));
                }
                float projX = px1 + t * (px2 - px1);
                float projY = py1 + t * (py2 - py1);
                float dist = (float) Math.sqrt((mouseX - projX) * (mouseX - projX) + (mouseY - projY) * (mouseY - projY));

                // ヒット判定範囲 (ディスプレイの高さの一定割合)
                float threshold = org.lwjgl.opengl.Display.getHeight() * 0.05f; // 5%の距離

                if (dist < threshold) {
                    float avgZ = (pz1 + pz2) / 2.0f;
                    if (avgZ < closestZ) {
                        closestZ = avgZ;
                        closestName = lastLoadedName;
                    }
                }
            }
            ci.cancel(); // ピッキングモード時は描画処理をスキップしてゴースト表示を防ぐ
        }
    }

    @Inject(method = "renderAnchorLine", at = @At("RETURN"), cancellable = true)
    private void onRenderAnchorLineReturn(TileEntityMarker marker, boolean isPickMode, MarkerElement hoveredElement, CallbackInfoReturnable<MarkerElement> cir) {
        if (isPickMode) {
            if (closestName != -1 && closestName != -2) {
                cir.setReturnValue(MarkerElement.values()[closestName]);
            } else {
                cir.setReturnValue(MarkerElement.NONE);
            }
            closestName = -2; // 判定終了
            closestZ = -1.0f;
        }
    }
}
