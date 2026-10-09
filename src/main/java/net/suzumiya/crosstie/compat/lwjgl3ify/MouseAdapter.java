package net.suzumiya.crosstie.compat.lwjgl3ify;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * lwjgl3ify 環境で {@code Mouse.isButtonDown(int)} がスクリプト(NGTOBuilder等)から
 * 正しく検知されない問題に対するブリッジ。
 *
 * <p>以下のいずれかが押下を示せば true を返す。
 * <ol>
 * <li>lwjgl3ify 本来の {@code org.lwjglx.input.Mouse.isButtonDown}</li>
 * <li>{@code Mouse.addButtonEvent} をフックして CrossTie が独自に追跡している状態</li>
 * <li>SDL の {@code SDL_GetMouseState} を直接参照した結果</li>
 * </ol>
 */
public final class MouseAdapter {

    private static final boolean[] TRACKED = new boolean[32];
    private static volatile Method sdlGetMouseState;
    private static volatile boolean sdlResolved;
    private static FloatBuffer posX;
    private static FloatBuffer posY;

    private MouseAdapter() {
    }

    /** Mixin から呼ばれる。ボタンイベントを記録する。 */
    public static void track(int button, boolean pressed) {
        if (button >= 0 && button < TRACKED.length) {
            TRACKED[button] = pressed;
        }
    }

    public static boolean isButtonDown(int button) {
        if (button < 0 || button >= TRACKED.length) {
            return false;
        }
        try {
            if (org.lwjglx.input.Mouse.isButtonDown(button)) {
                return true;
            }
        } catch (Throwable ignored) {
            // fall through to other sources
        }
        return mergeIsButtonDownResult(button, false);
    }

    /** 元の結果が false の場合に、追跡状態と SDL 直接参照で補完する。 */
    public static boolean mergeIsButtonDownResult(int button, boolean original) {
        if (original) {
            return true;
        }
        if (button < 0 || button >= TRACKED.length) {
            return false;
        }
        if (TRACKED[button]) {
            return true;
        }
        return querySdl(button);
    }

    private static synchronized boolean querySdl(int button) {
        if (!sdlResolved) {
            sdlResolved = true;
            for (String cls : new String[] { "org.lwjgl.sdl.SDLMouse", "org.lwjglx.sdl.SDLMouse" }) {
                try {
                    sdlGetMouseState = Class.forName(cls).getMethod("SDL_GetMouseState", FloatBuffer.class,
                            FloatBuffer.class);
                    posX = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).asFloatBuffer();
                    posY = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).asFloatBuffer();
                    break;
                } catch (Throwable ignored) {
                    // try next
                }
            }
        }
        if (sdlGetMouseState == null) {
            return false;
        }
        try {
            int flags = (Integer) sdlGetMouseState.invoke(null, posX, posY);
            // SDL: left=1, middle=2, right=3, x1=4, x2=5 / mask = 1 << (n-1)
            int sdlButton;
            switch (button) {
                case 0: sdlButton = 1; break;
                case 1: sdlButton = 3; break;
                case 2: sdlButton = 2; break;
                case 3: sdlButton = 4; break;
                case 4: sdlButton = 5; break;
                default: sdlButton = button + 1; break;
            }
            return (flags & (1 << (sdlButton - 1))) != 0;
        } catch (Throwable t) {
            sdlGetMouseState = null;
            return false;
        }
    }
}
