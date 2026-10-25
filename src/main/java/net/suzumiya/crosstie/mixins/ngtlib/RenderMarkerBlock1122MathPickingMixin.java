package net.suzumiya.crosstie.mixins.ngtlib;

import jp.ngt.rtm.rail.RenderMarkerBlock1122;
import jp.ngt.rtm.rail.RenderMarkerBlock1122.MarkerElement;
import jp.ngt.rtm.rail.TileEntityMarker;
import net.suzumiya.crosstie.utils.TrueGL;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * OptiFineやAngelica環境において、GL_SELECT（gluPickMatrix）による行列破壊や
 * スタブ化によるアンカー線ピッキング不能問題を回避し、
 * 視点空間クリッピングと投影計算による高精度な数学的ピッキングを行うMixin。
 */
@Mixin(value = RenderMarkerBlock1122.class, remap = false)
public class RenderMarkerBlock1122MathPickingMixin {

    private static int lastLoadedName = -1;
    private static int closestName = -1;
    private static float closestZ = Float.MAX_VALUE;
    private static boolean currentPickMode = false;

    private static final FloatBuffer modelMatrix = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer projMatrix = BufferUtils.createFloatBuffer(16);
    private static final IntBuffer viewport = BufferUtils.createIntBuffer(16);

    @Inject(method = "renderAnchorLine", at = @At("HEAD"))
    private void onRenderAnchorLineHead(TileEntityMarker marker, boolean isPickMode, MarkerElement hoveredElement, CallbackInfoReturnable<MarkerElement> cir) {
        net.suzumiya.crosstie.utils.MarkerRenderState.isMarkerRendering = true;
        currentPickMode = isPickMode;
        if (isPickMode) {
            closestName = -1;
            closestZ = Float.MAX_VALUE;
            TrueGL.glGetFloat(GL11.GL_PROJECTION_MATRIX, projMatrix);
            viewport.clear();
            GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        }
    }

    @Redirect(method = "renderAnchorLine", at = @At(value = "INVOKE", target = "Ljp/ngt/ngtlib/renderer/GLHelper;startMousePicking(F)V", remap = false))
    private void redirectStartMousePicking(float range) {
        // OptiFine環境で GL_SELECT や gluPickMatrix が適用されると行列が破壊され文字が巨大化するため、完全にバイパスする
    }

    @Redirect(method = "renderAnchorLine", at = @At(value = "INVOKE", target = "Ljp/ngt/ngtlib/renderer/GLHelper;finishMousePicking()I", remap = false))
    private int redirectFinishMousePicking() {
        return 0; // 常に0を返して元の判定ロジックをスキップさせる
    }

    @Redirect(method = "renderAnchorLine", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glLoadName(I)V", remap = false))
    private void redirectGlLoadName(int name) {
        lastLoadedName = name;
    }

    private static int crosstie$savedTextureId = 0;
    private static boolean crosstie$textureStateRedirected = false;

    private static boolean crosstie$isShaderEnabled() {
        if (jp.ngt.ngtlib.util.NGTUtilClient.usingShader()) {
            return true;
        }
        try {
            Class<?> clazz = Class.forName("shadersmod.client.Shaders");
            java.lang.reflect.Field field = clazz.getDeclaredField("shaderPackLoaded");
            return field.getBoolean(null);
        } catch (Throwable t) {
            return false;
        }
    }

    @Redirect(method = "renderAnchorLine", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V", remap = false))
    private void redirectGlDisable(int cap) {
        if (cap == GL11.GL_TEXTURE_2D && !currentPickMode && crosstie$isShaderEnabled()) {
            crosstie$savedTextureId = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, net.suzumiya.crosstie.utils.MarkerRenderState.getWhiteTexture());
            crosstie$textureStateRedirected = true;
        } else {
            GL11.glDisable(cap);
        }
    }

    @Redirect(method = "renderAnchorLine", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEnable(I)V", remap = false))
    private void redirectGlEnable(int cap) {
        if (cap == GL11.GL_TEXTURE_2D && crosstie$textureStateRedirected) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, crosstie$savedTextureId);
            crosstie$textureStateRedirected = false;
        } else {
            GL11.glEnable(cap);
        }
    }

    @Inject(method = "renderLine", at = @At("HEAD"), cancellable = true)
    private void onRenderLine(float startX, float startY, float startZ, float endX, float endY, float endZ, int color, CallbackInfo ci) {
        if (currentPickMode) {
            TrueGL.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelMatrix);

            float uX = modelMatrix.get(0),  uY = modelMatrix.get(1),  uZ = modelMatrix.get(2);
            float vX = modelMatrix.get(4),  vY = modelMatrix.get(5),  vZ = modelMatrix.get(6);
            float nX = modelMatrix.get(8),  nY = modelMatrix.get(9),  nZ = modelMatrix.get(10);
            float p0X = modelMatrix.get(12), p0Y = modelMatrix.get(13), p0Z = modelMatrix.get(14);

            // 視点空間 (Eye Space) での端点座標を算出 (カメラは原点、視線は -Z 方向)
            float x1 = p0X + startX * uX + startY * vX + startZ * nX;
            float y1 = p0Y + startX * uY + startY * vY + startZ * nY;
            float z1 = p0Z + startX * uZ + startY * vZ + startZ * nZ;

            float x2 = p0X + endX * uX + endY * vX + endZ * nX;
            float y2 = p0Y + endX * uY + endY * vY + endZ * nY;
            float z2 = p0Z + endX * uZ + endY * vZ + endZ * nZ;

            float zNear = -0.05f;

            // 両端ともカメラ背後 (z > zNear) にある場合は視界外のため完全にスキップ
            if (z1 > zNear && z2 > zNear) {
                ci.cancel();
                return;
            }

            // 片方がカメラ背後にある場合はニアクリップ面 z = zNear でクリッピングして反転誤爆を防止
            if (z1 > zNear) {
                float alpha = (zNear - z1) / (z2 - z1);
                x1 = x1 + alpha * (x2 - x1);
                y1 = y1 + alpha * (y2 - y1);
                z1 = zNear;
            } else if (z2 > zNear) {
                float alpha = (zNear - z2) / (z1 - z2);
                x2 = x2 + alpha * (x1 - x2);
                y2 = y2 + alpha * (y1 - y2);
                z2 = zNear;
            }

            // クリップ後の端点を Projection 行列でクリップ空間・NDCへ変換
            // projMatrix: column-major 4x4
            float m00 = projMatrix.get(0),  m10 = projMatrix.get(1),  m30 = projMatrix.get(3);
            float m01 = projMatrix.get(4),  m11 = projMatrix.get(5),  m31 = projMatrix.get(7);
            float m02 = projMatrix.get(8),  m12 = projMatrix.get(9),  m32 = projMatrix.get(11);
            float m03 = projMatrix.get(12), m13 = projMatrix.get(13), m33 = projMatrix.get(15);

            float w1 = m30 * x1 + m31 * y1 + m32 * z1 + m33;
            float w2 = m30 * x2 + m31 * y2 + m32 * z2 + m33;

            if (w1 > 1e-4f && w2 > 1e-4f) {
                float ndcX1 = (m00 * x1 + m01 * y1 + m02 * z1 + m03) / w1;
                float ndcY1 = (m10 * x1 + m11 * y1 + m12 * z1 + m13) / w1;

                float ndcX2 = (m00 * x2 + m01 * y2 + m02 * z2 + m03) / w2;
                float ndcY2 = (m10 * x2 + m11 * y2 + m12 * z2 + m13) / w2;

                int vpX = viewport.get(0);
                int vpY = viewport.get(1);
                int vpW = viewport.get(2);
                int vpH = viewport.get(3);

                float px1 = vpX + (1.0f + ndcX1) * vpW * 0.5f;
                float py1 = vpY + (1.0f + ndcY1) * vpH * 0.5f;

                float px2 = vpX + (1.0f + ndcX2) * vpW * 0.5f;
                float py2 = vpY + (1.0f + ndcY2) * vpH * 0.5f;

                float mouseX = vpX + vpW * 0.5f;
                float mouseY = vpY + vpH * 0.5f;

                float lineDx = px2 - px1;
                float lineDy = py2 - py1;
                float l2 = lineDx * lineDx + lineDy * lineDy;

                float t = 0.0f;
                if (l2 > 1e-4f) {
                    t = Math.max(0.0f, Math.min(1.0f, ((mouseX - px1) * lineDx + (mouseY - py1) * lineDy) / l2));
                }

                float projX = px1 + t * lineDx;
                float projY = py1 + t * lineDy;
                float dist = (float) Math.sqrt((mouseX - projX) * (mouseX - projX) + (mouseY - projY) * (mouseY - projY));

                // しきい値: 画面高さの約 1.8% (最小8px, 最大24px)
                float threshold = Math.max(8.0f, Math.min(24.0f, vpH * 0.018f));

                if (dist < threshold) {
                    // 視点空間での物理的なカメラ距離 (-z)
                    float depth = -(z1 + t * (z2 - z1));
                    if (depth > 0.05f && depth <= 64.0f && depth < closestZ) {
                        closestZ = depth;
                        closestName = lastLoadedName;
                    }
                }
            }

            ci.cancel(); // ピッキングモード時は描画処理をスキップしてゴースト表示を防ぐ
        }
    }

    @Inject(method = "renderAnchorLine", at = @At("RETURN"), cancellable = true)
    private void onRenderAnchorLineReturn(TileEntityMarker marker, boolean isPickMode, MarkerElement hoveredElement, CallbackInfoReturnable<MarkerElement> cir) {
        net.suzumiya.crosstie.utils.MarkerRenderState.isMarkerRendering = false;
        if (isPickMode) {
            if (closestName != -1) {
                cir.setReturnValue(MarkerElement.values()[closestName]);
            } else {
                cir.setReturnValue(MarkerElement.NONE);
            }
        }
    }
}
