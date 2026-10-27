package net.suzumiya.crosstie.mixins.ngtlib;

import jp.ngt.ngtlib.renderer.GLHelper;
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

    private static boolean crosstie$shadersChecked = false;
    private static java.lang.reflect.Field crosstie$shaderPackLoadedField = null;

    @SuppressWarnings("unused")
    private static boolean crosstie$isOptiFineShadersActive() {
        if (!crosstie$shadersChecked) {
            crosstie$shadersChecked = true;
            try {
                ClassLoader cl = GLHelperSelectBypassMixin.class.getClassLoader();
                if (cl.getResource("shadersmod/client/Shaders.class") != null) {
                    Class<?> shadersClass = Class.forName("shadersmod.client.Shaders", false, cl);
                    crosstie$shaderPackLoadedField = shadersClass.getDeclaredField("shaderPackLoaded");
                    crosstie$shaderPackLoadedField.setAccessible(true);
                }
            } catch (Throwable ignored) {
                crosstie$shaderPackLoadedField = null;
            }
        }
        if (crosstie$shaderPackLoadedField != null) {
            try {
                return crosstie$shaderPackLoadedField.getBoolean(null);
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    /**
     * @author Suzumiya
     * @reason OpenGL Core Profile (Angelica/Iris/lwjgl3ify) 環境下で廃止された GL_SELECT /
     *         glSelectBuffer によるJVMクラッシュを回避し、 CPU上での数学的ピッキング（Math Picking）に委譲する。
     */
    @Overwrite
    public static void startMousePicking(float range) {
        net.suzumiya.crosstie.utils.ActionPartsMathPicker.startPicking(range);
    }

    /**
     * @author Suzumiya
     * @reason OpenGL Core Profile (Angelica/Iris/lwjgl3ify) 環境下で廃止された GL_SELECT /
     *         glSelectBuffer によるJVMクラッシュを回避し、 CPU上での数学的ピッキング（Math Picking）に委譲する。
     */
    @Overwrite
    public static int finishMousePicking() {
        return net.suzumiya.crosstie.utils.ActionPartsMathPicker.finishPicking(SELECT_BUF);
    }
}
