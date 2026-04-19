package net.suzumiya.crosstie.compat;

import net.suzumiya.crosstie.config.CrossTieConfig;
import net.suzumiya.crosstie.util.AngelicaDirectBufferCache;
import net.suzumiya.crosstie.util.AngelicaRenderGuard;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Script GL bridge for Angelica with fast-path state caching and safe fallback.
 */
public final class AngelicaScriptGLBridge {

    private static final String GL_STATE_MANAGER_CLASS = "com.gtnewhorizons.angelica.glsm.GLStateManager";
    private static final Class<?> ANGELICA_GL_STATE_MANAGER;

    private static final Method M_GL_PUSH_MATRIX;
    private static final Method M_GL_POP_MATRIX;
    private static final Method M_GL_TRANSLATEF;
    private static final Method M_GL_ROTATEF;
    private static final Method M_GL_SCALEF;
    private static final Method M_GL_TRANSLATED;
    private static final Method M_GL_ROTATED;
    private static final Method M_GL_SCALED;
    private static final Method M_GL_MATRIX_MODE;
    private static final Method M_GL_LOAD_IDENTITY;
    private static final Method M_GL_BEGIN;
    private static final Method M_GL_END;
    private static final Method M_GL_VERTEX_3F;
    private static final Method M_GL_TEX_COORD_2F;
    private static final Method M_GL_NORMAL_3F;
    private static final Method M_GL_COLOR_4F;
    private static final Method M_GL_ENABLE;
    private static final Method M_GL_DISABLE;
    private static final Method M_GL_BLEND_FUNC;
    private static final Method M_GL_ALPHA_FUNC;
    private static final Method M_GL_DEPTH_MASK;
    private static final Method M_GL_SHADE_MODEL;

    private static final ThreadLocal<StateCache> STATE_CACHE = new ThreadLocal<StateCache>() {
        @Override
        protected StateCache initialValue() {
            return new StateCache();
        }
    };

    static {
        Class<?> glStateManager = null;
        try {
            glStateManager = Class.forName(GL_STATE_MANAGER_CLASS, false, AngelicaScriptGLBridge.class.getClassLoader());
        } catch (ClassNotFoundException ignored) {
            // Angelica not present.
        }
        ANGELICA_GL_STATE_MANAGER = glStateManager;

        M_GL_PUSH_MATRIX = findMethod("glPushMatrix");
        M_GL_POP_MATRIX = findMethod("glPopMatrix");
        M_GL_TRANSLATEF = findMethod("glTranslatef", float.class, float.class, float.class);
        M_GL_ROTATEF = findMethod("glRotatef", float.class, float.class, float.class, float.class);
        M_GL_SCALEF = findMethod("glScalef", float.class, float.class, float.class);
        M_GL_TRANSLATED = findMethod("glTranslated", double.class, double.class, double.class);
        M_GL_ROTATED = findMethod("glRotated", double.class, double.class, double.class, double.class);
        M_GL_SCALED = findMethod("glScaled", double.class, double.class, double.class);
        M_GL_MATRIX_MODE = findMethod("glMatrixMode", int.class);
        M_GL_LOAD_IDENTITY = findMethod("glLoadIdentity");
        M_GL_BEGIN = findMethod("glBegin", int.class);
        M_GL_END = findMethod("glEnd");
        M_GL_VERTEX_3F = findMethod("glVertex3f", float.class, float.class, float.class);
        M_GL_TEX_COORD_2F = findMethod("glTexCoord2f", float.class, float.class);
        M_GL_NORMAL_3F = findMethod("glNormal3f", float.class, float.class, float.class);
        M_GL_COLOR_4F = findMethod("glColor4f", float.class, float.class, float.class, float.class);
        M_GL_ENABLE = findMethod("glEnable", int.class);
        M_GL_DISABLE = findMethod("glDisable", int.class);
        M_GL_BLEND_FUNC = findMethod("glBlendFunc", int.class, int.class);
        M_GL_ALPHA_FUNC = findMethod("glAlphaFunc", int.class, float.class);
        M_GL_DEPTH_MASK = findMethod("glDepthMask", boolean.class);
        M_GL_SHADE_MODEL = findMethod("glShadeModel", int.class);
    }

    private AngelicaScriptGLBridge() {
    }

    private static Method findMethod(String name, Class<?>... parameterTypes) {
        if (ANGELICA_GL_STATE_MANAGER == null) {
            return null;
        }
        try {
            return ANGELICA_GL_STATE_MANAGER.getMethod(name, parameterTypes);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean invokeAngelica(Method method, Object... args) {
        if (shouldForceLegacy()) {
            return false;
        }
        if (method == null) {
            return false;
        }
        try {
            method.invoke(null, args);
            return true;
        } catch (ReflectiveOperationException ignored) {
            if (CrossTieConfig.enableAngelicaFallbackGuard) {
                AngelicaRenderGuard.triggerFallback();
            }
            return false;
        }
    }

    private static void callLegacy(Runnable call) {
        try {
            call.run();
        } catch (IllegalStateException ignored) {
            if (CrossTieConfig.enableAngelicaFallbackGuard) {
                AngelicaRenderGuard.triggerFallback();
            }
        }
    }

    private static boolean shouldForceLegacy() {
        return CrossTieConfig.enableAngelicaFallbackGuard && AngelicaRenderGuard.isFallbackActive();
    }

    private static boolean canUseAngelicaFastPath() {
        return CrossTieConfig.enableAngelicaFastPath && !shouldForceLegacy();
    }

    private static void stageFloats(float... values) {
        if (!CrossTieConfig.enableAngelicaFastPath) {
            return;
        }
        FloatBuffer buffer = AngelicaDirectBufferCache.copy(values);
        // Keep a direct-buffer copy hot in native memory for downstream Angelica upload paths.
        if (buffer.remaining() < 0) {
            AngelicaRenderGuard.triggerFallback();
        }
    }

    public static void glPushMatrix() {
        if (shouldForceLegacy()) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glPushMatrix();
                }
            });
            return;
        }

        if (!invokeAngelica(M_GL_PUSH_MATRIX)) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glPushMatrix();
                }
            });
        }
    }

    public static void glPopMatrix() {
        if (shouldForceLegacy()) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glPopMatrix();
                }
            });
            return;
        }

        if (!invokeAngelica(M_GL_POP_MATRIX)) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glPopMatrix();
                }
            });
        }
    }

    public static void glTranslatef(final float x, final float y, final float z) {
        if (AngelicaRenderGuard.hasInvalidFloat(x)
                || AngelicaRenderGuard.hasInvalidFloat(y)
                || AngelicaRenderGuard.hasInvalidFloat(z)) {
            AngelicaRenderGuard.triggerFallback();
        }
        stageFloats(x, y, z);
        if (!invokeAngelica(M_GL_TRANSLATEF, x, y, z) || shouldForceLegacy()) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glTranslatef(x, y, z);
                }
            });
        }
    }

    public static void glRotatef(final float angle, final float x, final float y, final float z) {
        if (AngelicaRenderGuard.hasInvalidFloat(angle)
                || AngelicaRenderGuard.hasInvalidFloat(x)
                || AngelicaRenderGuard.hasInvalidFloat(y)
                || AngelicaRenderGuard.hasInvalidFloat(z)) {
            AngelicaRenderGuard.triggerFallback();
        }
        stageFloats(angle, x, y, z);
        if (!invokeAngelica(M_GL_ROTATEF, angle, x, y, z) || shouldForceLegacy()) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glRotatef(angle, x, y, z);
                }
            });
        }
    }

    public static void glScalef(final float x, final float y, final float z) {
        if (AngelicaRenderGuard.hasInvalidFloat(x)
                || AngelicaRenderGuard.hasInvalidFloat(y)
                || AngelicaRenderGuard.hasInvalidFloat(z)) {
            AngelicaRenderGuard.triggerFallback();
        }
        stageFloats(x, y, z);
        if (!invokeAngelica(M_GL_SCALEF, x, y, z) || shouldForceLegacy()) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glScalef(x, y, z);
                }
            });
        }
    }

    public static void glTranslated(final double x, final double y, final double z) {
        if (AngelicaRenderGuard.hasInvalidDouble(x)
                || AngelicaRenderGuard.hasInvalidDouble(y)
                || AngelicaRenderGuard.hasInvalidDouble(z)) {
            AngelicaRenderGuard.triggerFallback();
        }
        stageFloats((float) x, (float) y, (float) z);
        if (!invokeAngelica(M_GL_TRANSLATED, x, y, z) || shouldForceLegacy()) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glTranslated(x, y, z);
                }
            });
        }
    }

    public static void glRotated(final double angle, final double x, final double y, final double z) {
        if (AngelicaRenderGuard.hasInvalidDouble(angle)
                || AngelicaRenderGuard.hasInvalidDouble(x)
                || AngelicaRenderGuard.hasInvalidDouble(y)
                || AngelicaRenderGuard.hasInvalidDouble(z)) {
            AngelicaRenderGuard.triggerFallback();
        }
        stageFloats((float) angle, (float) x, (float) y, (float) z);
        if (!invokeAngelica(M_GL_ROTATED, angle, x, y, z) || shouldForceLegacy()) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glRotated(angle, x, y, z);
                }
            });
        }
    }

    public static void glScaled(final double x, final double y, final double z) {
        if (AngelicaRenderGuard.hasInvalidDouble(x)
                || AngelicaRenderGuard.hasInvalidDouble(y)
                || AngelicaRenderGuard.hasInvalidDouble(z)) {
            AngelicaRenderGuard.triggerFallback();
        }
        stageFloats((float) x, (float) y, (float) z);
        if (!invokeAngelica(M_GL_SCALED, x, y, z) || shouldForceLegacy()) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glScaled(x, y, z);
                }
            });
        }
    }

    public static void glMatrixMode(final int mode) {
        StateCache cache = STATE_CACHE.get();
        if (canUseAngelicaFastPath() && cache.matrixMode == mode) {
            return;
        }
        cache.matrixMode = mode;

        if (!invokeAngelica(M_GL_MATRIX_MODE, mode) || shouldForceLegacy()) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glMatrixMode(mode);
                }
            });
        }
    }

    public static void glLoadIdentity() {
        if (!invokeAngelica(M_GL_LOAD_IDENTITY) || shouldForceLegacy()) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glLoadIdentity();
                }
            });
        }
    }

    public static void glBegin(final int mode) {
        if (!invokeAngelica(M_GL_BEGIN, mode) || shouldForceLegacy()) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glBegin(mode);
                }
            });
        }
    }

    public static void glEnd() {
        if (!invokeAngelica(M_GL_END) || shouldForceLegacy()) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glEnd();
                }
            });
        }
    }

    public static void glVertex3f(final float x, final float y, final float z) {
        if (AngelicaRenderGuard.hasInvalidFloat(x)
                || AngelicaRenderGuard.hasInvalidFloat(y)
                || AngelicaRenderGuard.hasInvalidFloat(z)) {
            AngelicaRenderGuard.triggerFallback();
            return;
        }
        stageFloats(x, y, z);
        if (!invokeAngelica(M_GL_VERTEX_3F, x, y, z) || shouldForceLegacy()) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glVertex3f(x, y, z);
                }
            });
        }
    }

    public static void glTexCoord2f(final float u, final float v) {
        if (!invokeAngelica(M_GL_TEX_COORD_2F, u, v) || shouldForceLegacy()) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glTexCoord2f(u, v);
                }
            });
        }
    }

    public static void glNormal3f(final float x, final float y, final float z) {
        if (!invokeAngelica(M_GL_NORMAL_3F, x, y, z) || shouldForceLegacy()) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glNormal3f(x, y, z);
                }
            });
        }
    }

    public static void glColor4f(final float r, final float g, final float b, final float a) {
        if (!invokeAngelica(M_GL_COLOR_4F, r, g, b, a) || shouldForceLegacy()) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glColor4f(r, g, b, a);
                }
            });
        }
    }

    public static void glEnable(final int cap) {
        StateCache cache = STATE_CACHE.get();
        if (canUseAngelicaFastPath() && cache.isEnabled(cap)) {
            return;
        }
        cache.setEnabled(cap, true);

        if (!invokeAngelica(M_GL_ENABLE, cap) || shouldForceLegacy()) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glEnable(cap);
                }
            });
        }
    }

    public static void glDisable(final int cap) {
        StateCache cache = STATE_CACHE.get();
        if (canUseAngelicaFastPath() && cache.isDisabled(cap)) {
            return;
        }
        cache.setEnabled(cap, false);

        if (!invokeAngelica(M_GL_DISABLE, cap) || shouldForceLegacy()) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glDisable(cap);
                }
            });
        }
    }

    public static void glBlendFunc(final int srcFactor, final int dstFactor) {
        StateCache cache = STATE_CACHE.get();
        if (canUseAngelicaFastPath() && cache.blendSrc == srcFactor && cache.blendDst == dstFactor) {
            return;
        }
        cache.blendSrc = srcFactor;
        cache.blendDst = dstFactor;

        if (!invokeAngelica(M_GL_BLEND_FUNC, srcFactor, dstFactor) || shouldForceLegacy()) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glBlendFunc(srcFactor, dstFactor);
                }
            });
        }
    }

    public static void glAlphaFunc(final int func, final float ref) {
        StateCache cache = STATE_CACHE.get();
        int refBits = Float.floatToIntBits(ref);
        if (canUseAngelicaFastPath() && cache.alphaFunc == func && cache.alphaRefBits == refBits) {
            return;
        }
        cache.alphaFunc = func;
        cache.alphaRefBits = refBits;

        if (!invokeAngelica(M_GL_ALPHA_FUNC, func, ref) || shouldForceLegacy()) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glAlphaFunc(func, ref);
                }
            });
        }
    }

    public static void glDepthMask(final boolean flag) {
        StateCache cache = STATE_CACHE.get();
        if (canUseAngelicaFastPath() && cache.depthMask != null && cache.depthMask.booleanValue() == flag) {
            return;
        }
        cache.depthMask = Boolean.valueOf(flag);

        if (!invokeAngelica(M_GL_DEPTH_MASK, flag) || shouldForceLegacy()) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glDepthMask(flag);
                }
            });
        }
    }

    public static void glShadeModel(final int mode) {
        StateCache cache = STATE_CACHE.get();
        if (canUseAngelicaFastPath() && cache.shadeModel == mode) {
            return;
        }
        cache.shadeModel = mode;

        if (!invokeAngelica(M_GL_SHADE_MODEL, mode) || shouldForceLegacy()) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glShadeModel(mode);
                }
            });
        }
    }

    private static final class StateCache {
        private final Map<Integer, Boolean> capEnableMap = new HashMap<Integer, Boolean>();
        private int matrixMode = Integer.MIN_VALUE;
        private int blendSrc = Integer.MIN_VALUE;
        private int blendDst = Integer.MIN_VALUE;
        private int alphaFunc = Integer.MIN_VALUE;
        private int alphaRefBits = Integer.MIN_VALUE;
        private Boolean depthMask;
        private int shadeModel = Integer.MIN_VALUE;

        private boolean isEnabled(int cap) {
            Boolean state = capEnableMap.get(Integer.valueOf(cap));
            return state != null && state.booleanValue();
        }

        private boolean isDisabled(int cap) {
            Boolean state = capEnableMap.get(Integer.valueOf(cap));
            return state != null && !state.booleanValue();
        }

        private void setEnabled(int cap, boolean enabled) {
            capEnableMap.put(Integer.valueOf(cap), Boolean.valueOf(enabled));
        }
    }
}
