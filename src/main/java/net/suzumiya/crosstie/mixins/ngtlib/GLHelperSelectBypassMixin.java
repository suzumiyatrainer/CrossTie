package net.suzumiya.crosstie.mixins.ngtlib;

import jp.ngt.ngtlib.renderer.GLHelper;
import jp.ngt.ngtlib.util.NGTUtilClient;
import net.minecraft.util.MathHelper;
import net.suzumiya.crosstie.utils.TrueGL;

import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.Project;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.IntBuffer;

/**
 * Angelica/GLSM によって GL_SELECT 関連のメソッドがスタブ化(機能停止)されているため、
 * リフレクション(TrueGL)経由で直接ドライバのGL_SELECTを呼び出すようにオーバーライトする。
 */
@Mixin(value = GLHelper.class, remap = false)
public abstract class GLHelperSelectBypassMixin {

    @Shadow
    private static IntBuffer VIEWPORT_BUF;
    @Shadow
    private static IntBuffer SELECT_BUF;
    @Shadow
    private static double DEPTH_RANGE;

    /**
     * @author Suzumiya
     * @reason AngelicaによるGL_SELECTのスタブ化を回避するため。
     */
    @Overwrite
    public static void startMousePicking(float range) {
        float mouseX = (float) Display.getWidth() / 2.0F;
        float mouseY = (float) Display.getHeight() / 2.0F;

        VIEWPORT_BUF.clear();
        SELECT_BUF.clear();

        GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT_BUF);

        // Angelicaのバイトコード置換を回避するためリフレクションで呼ぶ
        TrueGL.glSelectBuffer(SELECT_BUF);
        TrueGL.glRenderMode(GL11.GL_SELECT);
        TrueGL.glInitNames();
        TrueGL.glPushName(0);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        Project.gluPickMatrix(mouseX, VIEWPORT_BUF.get(3) - mouseY, range, range, VIEWPORT_BUF);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);

        DEPTH_RANGE = ((double) NGTUtilClient.getMinecraft().gameSettings.renderDistanceChunks * 16.0D
                * MathHelper.sqrt_float(2.0F)) - 0.05D;
    }

    /**
     * @author Suzumiya
     * @reason AngelicaによるGL_SELECTのスタブ化を回避するため。
     */
    @Overwrite
    public static int finishMousePicking() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();

        // Angelicaのバイトコード置換を回避するためリフレクションで呼ぶ
        int hits = TrueGL.glRenderMode(GL11.GL_RENDER);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        return hits;
    }
}
