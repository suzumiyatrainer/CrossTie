package net.suzumiya.crosstie.compat;

import java.lang.reflect.Method;
import org.lwjgl.opengl.GL11;

/**
 * スクリプト側から呼ばれる GL11 の仲介クラス。
 *
 * RTM のスクリプトは GL11 を直接呼ぶため、Angelica 環境では古い行列系 API が
 * 使えずに落ちることがあります。ここで既知の呼び出しを Angelica 側へ振り分け、
 * 使えない場合は LWJGL の GL11 にフォールバックします。
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

    static {
        Class<?> glStateManager = null;
        try {
            glStateManager = Class.forName(GL_STATE_MANAGER_CLASS, false, AngelicaScriptGLBridge.class.getClassLoader());
        } catch (ClassNotFoundException ignored) {
            // Angelica が無い場合
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
        if (method == null) {
            return false;
        }
        try {
            method.invoke(null, args);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static void callLegacy(Runnable call) {
        try {
            call.run();
        } catch (IllegalStateException ignored) {
            // 旧固定機能の入口が無くても描画を止めない
        }
    }

    public static void glPushMatrix() {
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
        if (!invokeAngelica(M_GL_TRANSLATEF, x, y, z)) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glTranslatef(x, y, z);
                }
            });
        }
    }

    public static void glRotatef(final float angle, final float x, final float y, final float z) {
        if (!invokeAngelica(M_GL_ROTATEF, angle, x, y, z)) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glRotatef(angle, x, y, z);
                }
            });
        }
    }

    public static void glScalef(final float x, final float y, final float z) {
        if (!invokeAngelica(M_GL_SCALEF, x, y, z)) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glScalef(x, y, z);
                }
            });
        }
    }

    public static void glTranslated(final double x, final double y, final double z) {
        if (!invokeAngelica(M_GL_TRANSLATED, x, y, z)) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glTranslated(x, y, z);
                }
            });
        }
    }

    public static void glRotated(final double angle, final double x, final double y, final double z) {
        if (!invokeAngelica(M_GL_ROTATED, angle, x, y, z)) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glRotated(angle, x, y, z);
                }
            });
        }
    }

    public static void glScaled(final double x, final double y, final double z) {
        if (!invokeAngelica(M_GL_SCALED, x, y, z)) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glScaled(x, y, z);
                }
            });
        }
    }

    public static void glMatrixMode(final int mode) {
        if (!invokeAngelica(M_GL_MATRIX_MODE, mode)) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glMatrixMode(mode);
                }
            });
        }
    }

    public static void glLoadIdentity() {
        if (!invokeAngelica(M_GL_LOAD_IDENTITY)) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glLoadIdentity();
                }
            });
        }
    }

    public static void glBegin(final int mode) {
        if (!invokeAngelica(M_GL_BEGIN, mode)) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glBegin(mode);
                }
            });
        }
    }

    public static void glEnd() {
        if (!invokeAngelica(M_GL_END)) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glEnd();
                }
            });
        }
    }

    public static void glVertex3f(final float x, final float y, final float z) {
        if (!invokeAngelica(M_GL_VERTEX_3F, x, y, z)) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glVertex3f(x, y, z);
                }
            });
        }
    }

    public static void glTexCoord2f(final float u, final float v) {
        if (!invokeAngelica(M_GL_TEX_COORD_2F, u, v)) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glTexCoord2f(u, v);
                }
            });
        }
    }

    public static void glNormal3f(final float x, final float y, final float z) {
        if (!invokeAngelica(M_GL_NORMAL_3F, x, y, z)) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glNormal3f(x, y, z);
                }
            });
        }
    }

    public static void glColor4f(final float r, final float g, final float b, final float a) {
        if (!invokeAngelica(M_GL_COLOR_4F, r, g, b, a)) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glColor4f(r, g, b, a);
                }
            });
        }
    }

    public static void glEnable(final int cap) {
        if (!invokeAngelica(M_GL_ENABLE, cap)) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glEnable(cap);
                }
            });
        }
    }

    public static void glDisable(final int cap) {
        if (!invokeAngelica(M_GL_DISABLE, cap)) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glDisable(cap);
                }
            });
        }
    }

    public static void glBlendFunc(final int srcFactor, final int dstFactor) {
        if (!invokeAngelica(M_GL_BLEND_FUNC, srcFactor, dstFactor)) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glBlendFunc(srcFactor, dstFactor);
                }
            });
        }
    }

    public static void glAlphaFunc(final int func, final float ref) {
        if (!invokeAngelica(M_GL_ALPHA_FUNC, func, ref)) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glAlphaFunc(func, ref);
                }
            });
        }
    }

    public static void glDepthMask(final boolean flag) {
        if (!invokeAngelica(M_GL_DEPTH_MASK, flag)) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glDepthMask(flag);
                }
            });
        }
    }

    public static void glShadeModel(final int mode) {
        if (!invokeAngelica(M_GL_SHADE_MODEL, mode)) {
            callLegacy(new Runnable() {
                @Override
                public void run() {
                    GL11.glShadeModel(mode);
                }
            });
        }
    }
}
