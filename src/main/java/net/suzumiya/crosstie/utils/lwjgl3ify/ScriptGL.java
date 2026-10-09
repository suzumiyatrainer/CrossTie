package net.suzumiya.crosstie.utils.lwjgl3ify;

import java.lang.reflect.Method;

/**
 * RTM の GUI（GuiSelectModel 等）から呼ばれる OpenGL 操作を Angelica の
 * {@code GLStateManager} 経由でルーティングするユーティリティクラス。
 *
 * <p>
 * lwjgl3ify 環境では Angelica が {@code GLStateManager} でレンダリング状態を 管理しているため、生の
 * {@code GL11} / {@code GL12} API を直接呼ぶと状態が desync し描画崩れやクラッシュが発生する。 このクラスは
 * Reflection を使用して Angelica の {@code GLStateManager} に委譲し、 コンパイル時に Angelica
 * への強依存を持たずに実行時解決する。
 *
 * <p>
 * このクラスは {@code AngelicaGlsm} が存在する場合のみロードされる
 * {@link net.suzumiya.crosstie.mixins.lwjgl3ify.GuiSelectModelRenderMixin} から
 * 呼ばれるため、Angelica 非存在環境でクラスが初期化されることはない。
 *
 * <p>
 * <b>原作:</b> LWJGL3ify-rtm-1.0.0 by 325 (LGPL-3.0-or-later)<br>
 * 本ファイルは LWJGL3ify-rtm より派生し CrossTie のパッケージへ移植・改変したものです。
 */
public final class ScriptGL {

    // ---- GL 定数（コンパイル時定数として lwjgl から転送） ----
    public static final int GL_TEXTURE_2D = org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
    public static final int GL_TRIANGLE_STRIP = org.lwjgl.opengl.GL11.GL_TRIANGLE_STRIP;
    public static final int GL_TRIANGLES = org.lwjgl.opengl.GL11.GL_TRIANGLES;
    public static final int GL_QUADS = org.lwjgl.opengl.GL11.GL_QUADS;
    public static final int GL_LINES = org.lwjgl.opengl.GL11.GL_LINES;
    public static final int GL_LINE_STRIP = org.lwjgl.opengl.GL11.GL_LINE_STRIP;
    public static final int GL_LINE_LOOP = org.lwjgl.opengl.GL11.GL_LINE_LOOP;
    public static final int GL_POINTS = org.lwjgl.opengl.GL11.GL_POINTS;
    public static final int GL_BLEND = org.lwjgl.opengl.GL11.GL_BLEND;
    public static final int GL_ALPHA_TEST = org.lwjgl.opengl.GL11.GL_ALPHA_TEST;
    public static final int GL_CULL_FACE = org.lwjgl.opengl.GL11.GL_CULL_FACE;
    public static final int GL_LIGHTING = org.lwjgl.opengl.GL11.GL_LIGHTING;
    public static final int GL_DEPTH_TEST = org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
    public static final int GL_SMOOTH = org.lwjgl.opengl.GL11.GL_SMOOTH;
    public static final int GL_FLAT = org.lwjgl.opengl.GL11.GL_FLAT;
    public static final int GL_SRC_ALPHA = org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
    public static final int GL_ONE_MINUS_SRC_ALPHA = org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
    public static final int GL_ONE = org.lwjgl.opengl.GL11.GL_ONE;
    public static final int GL_ZERO = org.lwjgl.opengl.GL11.GL_ZERO;
    public static final int GL_MODELVIEW = org.lwjgl.opengl.GL11.GL_MODELVIEW;
    public static final int GL_PROJECTION = org.lwjgl.opengl.GL11.GL_PROJECTION;
    public static final int GL_RESCALE_NORMAL = org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL;

    // ---- Reflection キャッシュ ----
    private static final Class<?> GLSM_CLASS = loadGlsmClass();
    private static final Method GL_PUSH_MATRIX = findMethod("glPushMatrix");
    private static final Method GL_POP_MATRIX = findMethod("glPopMatrix");
    private static final Method GL_PUSH_ATTRIB = findMethod("glPushAttrib", int.class);
    private static final Method GL_POP_ATTRIB = findMethod("glPopAttrib");
    private static final Method GL_TRANSLATE_F = findMethod("glTranslatef", float.class, float.class, float.class);
    private static final Method GL_TRANSLATED = findMethod("glTranslated", double.class, double.class, double.class);
    private static final Method GL_ROTATE_F = findMethod("glRotatef", float.class, float.class, float.class,
            float.class);
    private static final Method GL_ROTATED = findMethod("glRotated", double.class, double.class, double.class,
            double.class);
    private static final Method GL_SCALE_F = findMethod("glScalef", float.class, float.class, float.class);
    private static final Method GL_SCALED = findMethod("glScaled", double.class, double.class, double.class);
    private static final Method GL_MATRIX_MODE = findMethod("glMatrixMode", int.class);
    private static final Method GL_LOAD_IDENTITY = findMethod("glLoadIdentity");
    private static final Method GL_ORTHO = findMethod("glOrtho", double.class, double.class, double.class, double.class,
            double.class, double.class);
    private static final Method GL_ENABLE = findMethod("glEnable", int.class);
    private static final Method GL_DISABLE = findMethod("glDisable", int.class);
    private static final Method GL_BLEND_FUNC = findMethod("glBlendFunc", int.class, int.class);
    private static final Method GL_DEPTH_MASK = findMethod("glDepthMask", boolean.class);
    private static final Method GL_SHADE_MODEL = findMethod("glShadeModel", int.class);
    private static final Method GL_COLOR_4F = findMethod("glColor4f", float.class, float.class, float.class,
            float.class);
    private static final Method GL_COLOR_3F = findMethod("glColor3f", float.class, float.class, float.class);
    private static final Method GL_BEGIN = findMethod("glBegin", int.class);
    private static final Method GL_END = findMethod("glEnd");
    private static final Method GL_TEX_COORD_2F = findMethod("glTexCoord2f", float.class, float.class);
    private static final Method GL_VERTEX_3F = findMethod("glVertex3f", float.class, float.class, float.class);
    private static final Method GL_NORMAL_3F = findMethod("glNormal3f", float.class, float.class, float.class);
    private static final Method GL_BIND_TEXTURE = findMethod("glBindTexture", int.class, int.class);
    private static final Method GL_CALL_LIST = findMethod("glCallList", int.class);
    private static final Method GL_VIEWPORT = findMethod("glViewport", int.class, int.class, int.class, int.class);

    private ScriptGL() {
    }

    // ---- 公開 API ----

    public static void glPushMatrix() {
        invokeVoid(GL_PUSH_MATRIX);
    }

    public static void glPopMatrix() {
        invokeVoid(GL_POP_MATRIX);
    }

    public static void glPushAttrib(int mask) {
        invokeVoid(GL_PUSH_ATTRIB, Integer.valueOf(mask));
    }

    public static void glPopAttrib() {
        invokeVoid(GL_POP_ATTRIB);
    }

    public static void glTranslatef(float x, float y, float z) {
        invokeVoid(GL_TRANSLATE_F, Float.valueOf(x), Float.valueOf(y), Float.valueOf(z));
    }

    public static void glTranslated(double x, double y, double z) {
        invokeVoid(GL_TRANSLATED, Double.valueOf(x), Double.valueOf(y), Double.valueOf(z));
    }

    public static void glRotatef(float angle, float x, float y, float z) {
        invokeVoid(GL_ROTATE_F, Float.valueOf(angle), Float.valueOf(x), Float.valueOf(y), Float.valueOf(z));
    }

    public static void glRotated(double angle, double x, double y, double z) {
        invokeVoid(GL_ROTATED, Double.valueOf(angle), Double.valueOf(x), Double.valueOf(y), Double.valueOf(z));
    }

    public static void glScalef(float x, float y, float z) {
        invokeVoid(GL_SCALE_F, Float.valueOf(x), Float.valueOf(y), Float.valueOf(z));
    }

    public static void glScaled(double x, double y, double z) {
        invokeVoid(GL_SCALED, Double.valueOf(x), Double.valueOf(y), Double.valueOf(z));
    }

    public static void glMatrixMode(int mode) {
        invokeVoid(GL_MATRIX_MODE, Integer.valueOf(mode));
    }

    public static void glLoadIdentity() {
        invokeVoid(GL_LOAD_IDENTITY);
    }

    public static void glOrtho(double left, double right, double bottom, double top, double zNear, double zFar) {
        invokeVoid(GL_ORTHO, Double.valueOf(left), Double.valueOf(right), Double.valueOf(bottom), Double.valueOf(top),
                Double.valueOf(zNear), Double.valueOf(zFar));
    }

    public static void glEnable(int cap) {
        invokeVoid(GL_ENABLE, Integer.valueOf(cap));
    }

    public static void glDisable(int cap) {
        invokeVoid(GL_DISABLE, Integer.valueOf(cap));
    }

    public static void glBlendFunc(int src, int dst) {
        invokeVoid(GL_BLEND_FUNC, Integer.valueOf(src), Integer.valueOf(dst));
    }

    public static void glDepthMask(boolean flag) {
        invokeVoid(GL_DEPTH_MASK, Boolean.valueOf(flag));
    }

    public static void glShadeModel(int mode) {
        invokeVoid(GL_SHADE_MODEL, Integer.valueOf(mode));
    }

    public static void glColor4f(float r, float g, float b, float a) {
        invokeVoid(GL_COLOR_4F, Float.valueOf(r), Float.valueOf(g), Float.valueOf(b), Float.valueOf(a));
    }

    public static void glColor3f(float r, float g, float b) {
        invokeVoid(GL_COLOR_3F, Float.valueOf(r), Float.valueOf(g), Float.valueOf(b));
    }

    public static void glBegin(int mode) {
        invokeVoid(GL_BEGIN, Integer.valueOf(mode));
    }

    public static void glEnd() {
        invokeVoid(GL_END);
    }

    public static void glTexCoord2f(float s, float t) {
        invokeVoid(GL_TEX_COORD_2F, Float.valueOf(s), Float.valueOf(t));
    }

    public static void glVertex3f(float x, float y, float z) {
        invokeVoid(GL_VERTEX_3F, Float.valueOf(x), Float.valueOf(y), Float.valueOf(z));
    }

    public static void glNormal3f(float x, float y, float z) {
        invokeVoid(GL_NORMAL_3F, Float.valueOf(x), Float.valueOf(y), Float.valueOf(z));
    }

    public static void glBindTexture(int target, int texture) {
        invokeVoid(GL_BIND_TEXTURE, Integer.valueOf(target), Integer.valueOf(texture));
    }

    public static void glCallList(int list) {
        invokeVoid(GL_CALL_LIST, Integer.valueOf(list));
    }

    public static void glViewport(int x, int y, int width, int height) {
        invokeVoid(GL_VIEWPORT, Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(width),
                Integer.valueOf(height));
    }

    // ---- 内部ユーティリティ ----

    private static Class<?> loadGlsmClass() {
        try {
            return Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("[CrossTie/ScriptGL] Angelica GLStateManager が見つかりません。"
                    + "GuiSelectModelRenderMixin は AngelicaGlsm が存在する場合のみ適用されるはずです。", e);
        }
    }

    private static Method findMethod(String name, Class<?>... params) {
        try {
            Method m = GLSM_CLASS.getMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("[CrossTie/ScriptGL] GLStateManager." + name + " の解決に失敗しました。", e);
        }
    }

    private static void invokeVoid(Method m, Object... args) {
        try {
            m.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("[CrossTie/ScriptGL] GLStateManager." + m.getName() + " の呼び出しに失敗しました。", e);
        }
    }
}
