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

    static {
        try {
            Class<?> gl11 = Class.forName("org.lwjgl.opengl.GL11");
            m_glSelectBuffer = gl11.getMethod("glSelectBuffer", IntBuffer.class);
            m_glRenderMode = gl11.getMethod("glRenderMode", int.class);
            m_glInitNames = gl11.getMethod("glInitNames");
            m_glPushName = gl11.getMethod("glPushName", int.class);
            m_glLoadName = gl11.getMethod("glLoadName", int.class);
            m_glPopName = gl11.getMethod("glPopName");
            m_glGetInteger = gl11.getMethod("glGetInteger", int.class);
        } catch (Exception e) {
            e.printStackTrace();
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
            org.lwjgl.opengl.GL11.glMatrixMode(org.lwjgl.opengl.GL11.GL_PROJECTION);
            org.lwjgl.opengl.GL11.glLoadMatrix(buf);

            // Sync GL_MODELVIEW
            buf.clear();
            glGetFloatMethod.invoke(null, org.lwjgl.opengl.GL11.GL_MODELVIEW_MATRIX, buf);
            org.lwjgl.opengl.GL11.glMatrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);
            org.lwjgl.opengl.GL11.glLoadMatrix(buf);
        } catch (Exception e) {
            // AngelicaGlsmが導入されていない、またはリフレクションに失敗した場合は無視
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
}
