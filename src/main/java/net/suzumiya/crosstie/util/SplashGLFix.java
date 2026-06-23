package net.suzumiya.crosstie.util;

import java.lang.reflect.Method;
import java.lang.reflect.Field;

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

    private static Field angelicaTextures;
    private static Field angelicaClearColor;
    private static Field colorRed;
    private static Field colorGreen;
    private static Field colorBlue;
    private static Field colorAlpha;
    private static Method angelicaGetActiveTextureUnit;
    private static Method angelicaEnableTexture;
    private static Method angelicaClearCurrentColor;
    private static Method angelicaGetTextureUnitStates;
    private static Method angelicaGetTextureUnitBindings;
    private static Method angelicaSetUnknownState;
    private static Method angelicaSetBinding;
    private static boolean angelicaInitFailed;

    static {
        try {
            final Class<?> glStateManager = Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager");
            final Class<?> textureUnitArray = Class.forName("com.gtnewhorizons.angelica.glsm.states.TextureUnitArray");
            final Class<?> booleanState = Class.forName("com.gtnewhorizons.angelica.glsm.states.BooleanState");
            final Class<?> textureBinding = Class.forName("com.gtnewhorizons.angelica.glsm.states.TextureBinding");
            final Class<?> color4 = Class.forName("com.gtnewhorizons.angelica.glsm.states.Color4");

            angelicaTextures = glStateManager.getDeclaredField("textures");
            angelicaTextures.setAccessible(true);
            angelicaClearColor = glStateManager.getDeclaredField("clearColor");
            angelicaClearColor.setAccessible(true);
            colorRed = color4.getDeclaredField("red");
            colorGreen = color4.getDeclaredField("green");
            colorBlue = color4.getDeclaredField("blue");
            colorAlpha = color4.getDeclaredField("alpha");
            colorRed.setAccessible(true);
            colorGreen.setAccessible(true);
            colorBlue.setAccessible(true);
            colorAlpha.setAccessible(true);
            angelicaGetActiveTextureUnit = glStateManager.getDeclaredMethod("getActiveTextureUnit");
            angelicaEnableTexture = glStateManager.getDeclaredMethod("enableTexture");
            angelicaClearCurrentColor = glStateManager.getDeclaredMethod("clearCurrentColor");
            angelicaGetTextureUnitStates = textureUnitArray.getDeclaredMethod("getTextureUnitStates", int.class);
            angelicaGetTextureUnitBindings = textureUnitArray.getDeclaredMethod("getTextureUnitBindings", int.class);
            angelicaSetUnknownState = booleanState.getDeclaredMethod("setUnknownState");
            angelicaSetBinding = textureBinding.getDeclaredMethod("setBinding", int.class);
        } catch (Throwable ignored) {
            angelicaInitFailed = true;
            // Angelica is optional. Without Angelica there is no GLSM splash cache to repair.
        }
    }

    private SplashGLFix() {
    }

    /**
     * リフレクション経由で {@code GL11.glEnable(GL_TEXTURE_2D)} を呼び出す。
     * Angelica のバイトコードリダイレクターの影響を受けない。
     */
    public static void enableTexture2D() {
        markAngelicaTextureStateDirty();
        invokeAngelica(angelicaEnableTexture, "enableTexture");
    }

    /**
     * GL11.glEnable(GL_TEXTURE_2D) を強制的に呼び出す。
     * Angelica のバイトコードリダイレクトを完全にバイパスするため、
     * 直接 GL11 クラスのリフレクションを使用する。
     */
    public static void forceGLEnableTexture2D() {
        try {
            java.lang.reflect.Method glEnable = org.lwjgl.opengl.GL11.class.getMethod("glEnable", int.class);
            glEnable.invoke(null, org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
        } catch (Throwable e) {
            System.err.println("[CrossTie] SplashGLFix: forceGLEnable failed: " + e.getMessage());
        }
    }

    /**
     * リフレクション経由で {@code GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f)} を呼び出す。
     * Angelica のバイトコードリダイレクターの影響を受けない。
     */
    public static void resetColor() {
        invokeAngelica(angelicaClearCurrentColor, "clearCurrentColor");
    }

    public static void prepareTexturedSplashDraw() {
        enableTexture2D();
        resetColor();
    }

    public static void markSplashStateDirty() {
        markAngelicaTextureStateDirty();
        markAngelicaClearColorDirty();
        resetColor();
    }

    private static void invokeAngelica(Method method, String name) {
        if (angelicaInitFailed || method == null) return;
        try {
            method.invoke(null);
        } catch (Throwable e) {
            System.err.println("[CrossTie] SplashGLFix: GLStateManager." + name + " failed: " + e.getMessage());
        }
    }

    private static void markAngelicaTextureStateDirty() {
        if (angelicaInitFailed) return;
        try {
            int activeUnit = ((Integer) angelicaGetActiveTextureUnit.invoke(null)).intValue();
            Object textures = angelicaTextures.get(null);

            Object textureState = angelicaGetTextureUnitStates.invoke(textures, activeUnit);
            angelicaSetUnknownState.invoke(textureState);

            Object textureBinding = angelicaGetTextureUnitBindings.invoke(textures, activeUnit);
            angelicaSetBinding.invoke(textureBinding, -1);
        } catch (Throwable e) {
            System.err.println("[CrossTie] SplashGLFix: failed to dirty Angelica texture cache: " + e.getMessage());
        }
    }

    private static void markAngelicaClearColorDirty() {
        if (angelicaInitFailed) return;
        try {
            Object clearColor = angelicaClearColor.get(null);
            colorRed.setFloat(clearColor, Float.NaN);
            colorGreen.setFloat(clearColor, Float.NaN);
            colorBlue.setFloat(clearColor, Float.NaN);
            colorAlpha.setFloat(clearColor, Float.NaN);
        } catch (Throwable e) {
            System.err.println("[CrossTie] SplashGLFix: failed to dirty Angelica clear color cache: " + e.getMessage());
        }
    }
}
