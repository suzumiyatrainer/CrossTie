package net.suzumiya.crosstie.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

/**
 * 3-3: {@code ModelObject.render()} スコープ内での冗長な {@code GL11.glShadeModel(GL_SMOOTH / GL_FLAT)}
 * 切替を 2N 回 → 2 回へ削減するトラッカー（クライアントレンダースレッド専用）。
 *
 * <ul>
 *   <li>{@code ModelObject.render()} 経由の描画ではスコープ開始時に 1 回だけ {@code GL_SMOOTH}、
 *       終了時に 1 回だけ {@code GL_FLAT} を発行し、内部の {@code Parts.render()} /
 *       {@code PartsRenderer.renderLightEffect()} での冗長切替を完全スキップする。</li>
 *   <li>{@code ModelObject.render()} を経由しない直接 {@code Parts.render()} 呼び出し時は
 *       従来通り {@code GL_SMOOTH} → {@code GL_FLAT} を発行し、OptiFine 固定機能パイプラインの
 *       {@code GL_FLAT} 前提を 100% 保証する。</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class CrossTieShadeModelTracker {

    private static int smoothScopeDepth = 0;
    private static boolean isCurrentlySmooth = false;

    private CrossTieShadeModelTracker() {
    }

    public static void enterModelSmoothScope() {
        if (smoothScopeDepth == 0) {
            GL11.glShadeModel(GL11.GL_SMOOTH);
            isCurrentlySmooth = true;
        }
        smoothScopeDepth++;
    }

    public static void exitModelSmoothScope() {
        if (smoothScopeDepth <= 0) {
            smoothScopeDepth = 0;
            GL11.glShadeModel(GL11.GL_FLAT);
            isCurrentlySmooth = false;
            return;
        }
        smoothScopeDepth--;
        if (smoothScopeDepth == 0) {
            if (isCurrentlySmooth) {
                GL11.glShadeModel(GL11.GL_FLAT);
                isCurrentlySmooth = false;
            }
        }
    }

    public static void applyShadeModel(int mode) {
        if (mode == GL11.GL_SMOOTH) {
            if (smoothScopeDepth > 0) {
                if (!isCurrentlySmooth) {
                    GL11.glShadeModel(GL11.GL_SMOOTH);
                    isCurrentlySmooth = true;
                }
            } else {
                GL11.glShadeModel(GL11.GL_SMOOTH);
                isCurrentlySmooth = true;
            }
        } else {
            if (smoothScopeDepth > 0) {
                return;
            }
            GL11.glShadeModel(mode);
            isCurrentlySmooth = false;
        }
    }
}
