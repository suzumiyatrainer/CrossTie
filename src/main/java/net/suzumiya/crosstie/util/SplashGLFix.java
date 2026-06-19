package net.suzumiya.crosstie.util;

import java.lang.reflect.Method;

/**
 * リフレクション経由で GL11 のメソッドを呼び出すユーティリティ。
 *
 * <p>Angelica の ASM トランスフォーマはロードされる全クラスの {@code GL11.glEnable()} などの
 * 呼び出しを {@code GLStateManager.glEnable()} に書き換える。その結果、キャッシュ更新のみで
 * 実際の OpenGL コールが行われなくなる。</p>
 *
 * <p>このクラスはリフレクションで GL11 のメソッドを直接呼び出すことで、Angelica の
 * バイトコードリダイレクトをバイパスする。</p>
 */
public final class SplashGLFix {

    private static Method glEnable;
    private static Method glColor4f;
    private static boolean initFailed;

    static {
        try {
            final Class<?> gl11 = Class.forName("org.lwjgl.opengl.GL11");
            glEnable = gl11.getDeclaredMethod("glEnable", int.class);
            glColor4f = gl11.getDeclaredMethod("glColor4f", float.class, float.class, float.class, float.class);
        } catch (Exception e) {
            initFailed = true;
            System.err.println("[CrossTie] SplashGLFix: Failed to initialize GL11 reflection: " + e.getMessage());
        }
    }

    private SplashGLFix() {
    }

    /**
     * リフレクション経由で {@code GL11.glEnable(GL_TEXTURE_2D)} を呼び出す。
     * Angelica のバイトコードリダイレクターの影響を受けない。
     */
    public static void enableTexture2D() {
        if (initFailed) return;
        try {
            glEnable.invoke(null, 0x0DE1); // GL_TEXTURE_2D = 0x0DE1
        } catch (Exception e) {
            System.err.println("[CrossTie] SplashGLFix: glEnable failed: " + e.getMessage());
        }
    }

    /**
     * リフレクション経由で {@code GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f)} を呼び出す。
     * Angelica のバイトコードリダイレクターの影響を受けない。
     */
    public static void resetColor() {
        if (initFailed) return;
        try {
            glColor4f.invoke(null, 1.0f, 1.0f, 1.0f, 1.0f);
        } catch (Exception e) {
            System.err.println("[CrossTie] SplashGLFix: glColor4f failed: " + e.getMessage());
        }
    }
}
