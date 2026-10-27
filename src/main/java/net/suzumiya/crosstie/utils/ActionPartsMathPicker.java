package net.suzumiya.crosstie.utils;

import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * OpenGL Core Profile (Angelica/Iris/lwjgl3ify) 環境において
 * 廃止された GL_SELECT / glSelectBuffer を使用せず、
 * CPU上で視線（画面中央またはマウスカーソル）とパーツモデルのポリゴン幾何交差判定を行うMath Pickingマネージャー。
 */
public final class ActionPartsMathPicker {

    private static boolean isPickingActive = false;
    private static int currentPartId = 0;
    private static int pickedPartId = 0;
    private static double minDepth = Double.MAX_VALUE;
    private static float pickMargin = 0.025f;
    private static float maxReachDistance = 5.0f;

    private static float targetX = 0.0f;
    private static float targetY = 0.0f;

    private static final FloatBuffer modelViewBuf = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer projBuf = BufferUtils.createFloatBuffer(16);
    private static final float[] mv = new float[16];
    private static final float[] pr = new float[16];
    private static final float[] mvp = new float[16];

    private static Field irisShadowActiveField = null;
    private static boolean irisShadowChecked = false;
    private static Field optifineShadowPassField = null;
    private static boolean optifineShadowChecked = false;

    private ActionPartsMathPicker() {
    }

    public static boolean isPickingActive() {
        return isPickingActive;
    }

    public static void setCurrentPartId(int id) {
        currentPartId = id;
    }

    /**
     * IrisまたはOptiFineの影レンダリングパス（Shadow Pass）中かどうかを判定する。
     * 影パス中はプレイヤーの視点ではなく太陽/光源の視点であるため、ピッキングは絶対に実行してはならない。
     */
    public static boolean isShadowPassActive() {
        if (!irisShadowChecked) {
            irisShadowChecked = true;
            try {
                Class<?> clazz = Class.forName("net.coderbot.iris.pipeline.ShadowRenderer");
                irisShadowActiveField = clazz.getField("ACTIVE");
            } catch (Throwable ignored) {
            }
        }
        if (irisShadowActiveField != null) {
            try {
                if (irisShadowActiveField.getBoolean(null)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }

        if (!optifineShadowChecked) {
            optifineShadowChecked = true;
            try {
                Class<?> clazz = Class.forName("shadersmod.client.Shaders");
                optifineShadowPassField = clazz.getDeclaredField("isShadowPass");
                optifineShadowPassField.setAccessible(true);
            } catch (Throwable ignored) {
            }
        }
        if (optifineShadowPassField != null) {
            try {
                if (optifineShadowPassField.getBoolean(null)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }

        return false;
    }

    /**
     * ピッキング開始
     */
    public static void startPicking(float range) {
        if (isShadowPassActive()) {
            isPickingActive = false;
            return;
        }

        isPickingActive = true;
        currentPartId = 0;
        pickedPartId = 0;
        minDepth = Double.MAX_VALUE;

        // マウス位置の判定
        // 通常のプレイ（一人称運転など、マウスがグラブされている時）は画面中央 (0, 0)
        // インベントリやGUI操作等でマウスカーソルが自由な時はマウスポインタの現在位置
        if (Mouse.isGrabbed()) {
            targetX = 0.0f;
            targetY = 0.0f;
        } else {
            int dispW = Math.max(1, Display.getWidth());
            int dispH = Math.max(1, Display.getHeight());
            targetX = (Mouse.getX() / (float) dispW) * 2.0f - 1.0f;
            targetY = (Mouse.getY() / (float) dispH) * 2.0f - 1.0f;
        }

        // クリック判定の許容マージン（NDCで約0.025 = 1080pで約27pxの吸い付き範囲）
        pickMargin = 0.025f;

        // 手の届く距離（リーチ）の上限を設定
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc != null && mc.thePlayer != null && mc.thePlayer.ridingEntity != null) {
            maxReachDistance = 6.0f; // 車両に乗車中は運転席周りのパーツに確実に届く
        } else {
            maxReachDistance = 4.5f; // 徒歩時は近距離（手の届く範囲）のみ
        }
    }

    /**
     * ピッキング終了
     * @param selectBuf GLHelper.SELECT_BUF
     * @return ヒット数 (0 または 1)
     */
    public static int finishPicking(IntBuffer selectBuf) {
        if (!isPickingActive) {
            return 0;
        }
        isPickingActive = false;

        selectBuf.clear();
        if (pickedPartId > 0) {
            // GLHelper.getPickedObjId(0) -> SELECT_BUF.get(3)
            // GLHelper.getPickedObjDepth(0) -> SELECT_BUF.get(1)
            selectBuf.put(0, 1); // hit count
            double depthRange = 100.0D;
            long depthInt = (long) Math.max(0, Math.min(4294967295L, (minDepth - 0.05D) / depthRange * 4294967295.0D));
            int d = (int) (depthInt - 2147483648L);
            selectBuf.put(1, d); // minZ
            selectBuf.put(2, d); // maxZ
            selectBuf.put(3, pickedPartId); // hit ID
            return 1;
        }

        return 0;
    }

    /**
     * NGTTessellator の頂点バッファを評価し、ターゲット点（クロスヘア）との交差を判定する。
     */
    public static void processTessellator(int drawMode, int vertexCount, int[] rawBuffer) {
        if (!isPickingActive || currentPartId <= 0 || vertexCount < 3) {
            return;
        }

        // 現在の ModelView 行列と Projection 行列を取得
        modelViewBuf.clear();
        projBuf.clear();
        TrueGL.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelViewBuf);
        TrueGL.glGetFloat(GL11.GL_PROJECTION_MATRIX, projBuf);
        modelViewBuf.get(mv);
        projBuf.get(pr);

        // MVP = P * V (Column-Major 4x4)
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                mvp[j * 4 + i] = pr[0 * 4 + i] * mv[j * 4 + 0] + pr[1 * 4 + i] * mv[j * 4 + 1]
                        + pr[2 * 4 + i] * mv[j * 4 + 2] + pr[3 * 4 + i] * mv[j * 4 + 3];
            }
        }

        // 各頂点のクリップ座標とNDC座標を算出
        float[] ndcX = new float[vertexCount];
        float[] ndcY = new float[vertexCount];
        float[] clipW = new float[vertexCount];
        boolean[] valid = new boolean[vertexCount];

        for (int i = 0; i < vertexCount; i++) {
            int base = i * 8;
            float x = Float.intBitsToFloat(rawBuffer[base]);
            float y = Float.intBitsToFloat(rawBuffer[base + 1]);
            float z = Float.intBitsToFloat(rawBuffer[base + 2]);

            float cx = mvp[0] * x + mvp[4] * y + mvp[8] * z + mvp[12];
            float cy = mvp[1] * x + mvp[5] * y + mvp[9] * z + mvp[13];
            float cw = mvp[3] * x + mvp[7] * y + mvp[11] * z + mvp[15];

            clipW[i] = cw;
            if (cw > 0.05f) { // ニアクリップ面の前方
                ndcX[i] = cx / cw;
                ndcY[i] = cy / cw;
                valid[i] = true;
            } else {
                valid[i] = false;
            }
        }

        // 描画モードに応じてポリゴンごとに判定
        if (drawMode == GL11.GL_TRIANGLES) {
            for (int i = 0; i < vertexCount - 2; i += 3) {
                testTriangle(ndcX, ndcY, clipW, valid, i, i + 1, i + 2);
            }
        } else if (drawMode == GL11.GL_QUADS) {
            for (int i = 0; i < vertexCount - 3; i += 4) {
                testTriangle(ndcX, ndcY, clipW, valid, i, i + 1, i + 2);
                testTriangle(ndcX, ndcY, clipW, valid, i, i + 2, i + 3);
            }
        } else if (drawMode == GL11.GL_TRIANGLE_STRIP) {
            for (int i = 0; i < vertexCount - 2; i++) {
                if ((i & 1) == 0) {
                    testTriangle(ndcX, ndcY, clipW, valid, i, i + 1, i + 2);
                } else {
                    testTriangle(ndcX, ndcY, clipW, valid, i + 1, i, i + 2);
                }
            }
        } else if (drawMode == GL11.GL_TRIANGLE_FAN) {
            for (int i = 1; i < vertexCount - 1; i++) {
                testTriangle(ndcX, ndcY, clipW, valid, 0, i, i + 1);
            }
        }
    }

    private static boolean isPointNearSegment(float px, float py, float x0, float y0, float x1, float y1, float marginSq) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float l2 = dx * dx + dy * dy;
        if (l2 == 0.0f) {
            float ddx = px - x0;
            float ddy = py - y0;
            return (ddx * ddx + ddy * ddy) <= marginSq;
        }
        float t = Math.max(0.0f, Math.min(1.0f, ((px - x0) * dx + (py - y0) * dy) / l2));
        float projX = x0 + t * dx;
        float projY = y0 + t * dy;
        float distSq = (px - projX) * (px - projX) + (py - projY) * (py - projY);
        return distSq <= marginSq;
    }

    private static void testTriangle(float[] nx, float[] ny, float[] cw, boolean[] valid, int i0, int i1, int i2) {
        if (!valid[i0] && !valid[i1] && !valid[i2]) {
            return;
        }

        float x0 = nx[i0], y0 = ny[i0];
        float x1 = nx[i1], y1 = ny[i1];
        float x2 = nx[i2], y2 = ny[i2];

        // 2D AABB 判定 (粗削りバウンディングボックスフィルタ)
        float minX = Math.min(x0, Math.min(x1, x2)) - pickMargin;
        float maxX = Math.max(x0, Math.max(x1, x2)) + pickMargin;
        float minY = Math.min(y0, Math.min(y1, y2)) - pickMargin;
        float maxY = Math.max(y0, Math.max(y1, y2)) + pickMargin;

        if (targetX < minX || targetX > maxX || targetY < minY || targetY > maxY) {
            return;
        }

        // 厳密な外積計算（点 P と各辺ベクトル）
        float c0 = (x1 - x0) * (targetY - y0) - (y1 - y0) * (targetX - x0);
        float c1 = (x2 - x1) * (targetY - y1) - (y2 - y1) * (targetX - x1);
        float c2 = (x0 - x2) * (targetY - y2) - (y0 - y2) * (targetX - x2);

        // 厳密な三角形内部判定 (全ての外積の符号が一致しているか)
        boolean inside = (c0 >= 0 && c1 >= 0 && c2 >= 0) || (c0 <= 0 && c1 <= 0 && c2 <= 0);

        // 内部にない場合、3本の線分のいずれかから pickMargin 以内にあるか判定
        if (!inside) {
            float mSq = pickMargin * pickMargin;
            if (!isPointNearSegment(targetX, targetY, x0, y0, x1, y1, mSq)
                    && !isPointNearSegment(targetX, targetY, x1, y1, x2, y2, mSq)
                    && !isPointNearSegment(targetX, targetY, x2, y2, x0, y0, mSq)) {
                return;
            }
        }

        // ヒット！カメラからの距離（Eye-space Z、手の届く距離以内）
        float depth = Math.min(cw[i0], Math.min(cw[i1], cw[i2]));
        if (depth > 0.05f && depth <= maxReachDistance && depth < minDepth) {
            minDepth = depth;
            pickedPartId = currentPartId;
        }
    }
}
