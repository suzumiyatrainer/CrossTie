package net.suzumiya.crosstie.utils;

import java.lang.reflect.Method;
import java.nio.IntBuffer;
import java.nio.FloatBuffer;

/**
 * Angelica/GLSM によってスタブ化(無効化)された GL_SELECT 関連のメソッドを
 * リフレクション経由で強制的に呼び出すためのユーティリティ。 ASMによるバイトコード置換(GLSMRedirector)を回避し、グラフィックドライバの
 * ネイティブ機能へ直接アクセスする。
 */
public class TrueGL {
    private static Method m_glSelectBuffer;
    private static Method m_glRenderMode;
    private static Method m_glInitNames;
    private static Method m_glPushName;
    private static Method m_glLoadName;
    private static Method m_glPopName;
    private static Method m_glGetInteger;
    private static Method m_glMatrixMode;
    private static Method m_glLoadMatrix;
    private static Method m_glBegin;
    private static Method m_glVertex3f;
    private static Method m_glEnd;
    private static Method m_angelicaGlGetFloat;

    static {
        Class<?> gl11 = null;
        try {
            gl11 = Class.forName("org.lwjgl.opengl.GL11");
        } catch (Throwable e) {
            e.printStackTrace();
        }
        if (gl11 != null) {
            m_glSelectBuffer = find(gl11, "glSelectBuffer", IntBuffer.class);
            m_glRenderMode = find(gl11, "glRenderMode", int.class);
            m_glInitNames = find(gl11, "glInitNames");
            m_glPushName = find(gl11, "glPushName", int.class);
            m_glLoadName = find(gl11, "glLoadName", int.class);
            m_glPopName = find(gl11, "glPopName");
            m_glGetInteger = find(gl11, "glGetInteger", int.class);
            m_glMatrixMode = find(gl11, "glMatrixMode", int.class);
            // LWJGL2: glLoadMatrix / LWJGL3: glLoadMatrixf
            m_glLoadMatrix = find(gl11, "glLoadMatrix", FloatBuffer.class);
            if (m_glLoadMatrix == null) {
                m_glLoadMatrix = find(gl11, "glLoadMatrixf", FloatBuffer.class);
            }
            m_glBegin = find(gl11, "glBegin", int.class);
            m_glVertex3f = find(gl11, "glVertex3f", float.class, float.class, float.class);
            m_glEnd = find(gl11, "glEnd");
        }

        try {
            Class<?> glStateManagerClass = Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager");
            m_angelicaGlGetFloat = glStateManagerClass.getMethod("glGetFloat", int.class, FloatBuffer.class);
        } catch (Throwable ignored) {
        }
    }

    private static Method find(Class<?> cls, String name, Class<?>... params) {
        try {
            return cls.getMethod(name, params);
        } catch (Throwable t) {
            return null;
        }
    }

    public static void glSelectBuffer(IntBuffer buffer) {
        try {
            if (m_glSelectBuffer != null)
                m_glSelectBuffer.invoke(null, buffer);
        } catch (Exception ignored) {
        }
    }

    private static boolean isSelectMode = false;

    public static boolean isSelectMode() {
        return isSelectMode;
    }

    public static int glRenderMode(int mode) {
        isSelectMode = (mode == 7170); // GL11.GL_SELECT is 7170 (0x1C02)
        try {
            if (m_glRenderMode != null)
                return (int) m_glRenderMode.invoke(null, mode);
        } catch (Exception ignored) {
        }
        return 0;
    }

    /**
     * Angelicaが管理するソフトウェア行列(モデルビュー、プロジェクション)を NATIVEドライバ側の行列スタックへ強制的に同期させます。
     * AngelicaのTessellatorをバイパスして、GL_SELECTなどのNATIVEモードで
     * 即時描画(glBegin/glEnd)を行う際に必須となります。
     */
    public static void syncMatricesToDriver() {
        try {
            Class<?> glStateManagerClass = Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager");
            Method glGetFloatMethod = glStateManagerClass.getMethod("glGetFloat", int.class, FloatBuffer.class);

            FloatBuffer buf = org.lwjgl.BufferUtils.createFloatBuffer(16);

            // Sync GL_PROJECTION
            glGetFloatMethod.invoke(null, org.lwjgl.opengl.GL11.GL_PROJECTION_MATRIX, buf);
            glMatrixMode(org.lwjgl.opengl.GL11.GL_PROJECTION);
            glLoadMatrix(buf);

            // Sync GL_MODELVIEW
            buf.clear();
            glGetFloatMethod.invoke(null, org.lwjgl.opengl.GL11.GL_MODELVIEW_MATRIX, buf);
            glMatrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);
            glLoadMatrix(buf);
        } catch (Exception e) {
            // AngelicaGlsmが導入されていない、またはリフレクションに失敗した場合は無視
        }
    }

    public static void glMatrixMode(int mode) {
        try {
            if (m_glMatrixMode != null)
                m_glMatrixMode.invoke(null, mode);
        } catch (Exception ignored) {
        }
    }

    public static void glLoadMatrix(FloatBuffer buffer) {
        try {
            if (m_glLoadMatrix != null)
                m_glLoadMatrix.invoke(null, buffer);
        } catch (Exception ignored) {
        }
    }

    public static void glBegin(int mode) {
        try {
            if (m_glBegin != null)
                m_glBegin.invoke(null, mode);
        } catch (Exception ignored) {
        }
    }

    public static void glVertex3f(float x, float y, float z) {
        try {
            if (m_glVertex3f != null)
                m_glVertex3f.invoke(null, x, y, z);
        } catch (Exception ignored) {
        }
    }

    public static void glEnd() {
        try {
            if (m_glEnd != null)
                m_glEnd.invoke(null);
        } catch (Exception ignored) {
        }
    }

    public static void glInitNames() {
        try {
            if (m_glInitNames != null)
                m_glInitNames.invoke(null);
        } catch (Exception ignored) {
        }
    }

    public static void glPushName(int name) {
        try {
            if (m_glPushName != null)
                m_glPushName.invoke(null, name);
        } catch (Exception ignored) {
        }
    }

    public static void glLoadName(int name) {
        try {
            if (m_glLoadName != null)
                m_glLoadName.invoke(null, name);
        } catch (Exception ignored) {
        }
    }

    public static void glPopName() {
        try {
            if (m_glPopName != null)
                m_glPopName.invoke(null);
        } catch (Exception ignored) {
        }
    }

    public static int glGetInteger(int pname) {
        try {
            if (m_glGetInteger != null)
                return (int) m_glGetInteger.invoke(null, pname);
        } catch (Exception ignored) {
        }
        return 0;
    }

    public static void glGetFloat(int pname, FloatBuffer params) {
        params.clear();
        if (m_angelicaGlGetFloat != null) {
            try {
                m_angelicaGlGetFloat.invoke(null, pname, params);
                return;
            } catch (Exception ignored) {
            }
        }
        org.lwjgl.opengl.GL11.glGetFloat(pname, params);
    }
}
