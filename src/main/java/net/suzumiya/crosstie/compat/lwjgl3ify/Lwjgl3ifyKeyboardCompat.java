package net.suzumiya.crosstie.compat.lwjgl3ify;

import java.nio.ByteBuffer;

/** Runtime fallback used by the early LaunchWrapper transformer. */
public final class Lwjgl3ifyKeyboardCompat {
    private Lwjgl3ifyKeyboardCompat() {}

    public static boolean mergeIsKeyDownResult(int key, boolean originalResult) {
        if (originalResult || key == org.lwjglx.input.Keyboard.KEY_NONE) {
            Lwjgl3ifyKeyboardDebug.report(key, originalResult, "source=lwjgl3ify");
            return originalResult;
        }

        int staticScancode = -1;
        int sdlKeycode = -1;
        int layoutScancode = -1;
        boolean staticDown = false;
        boolean layoutDown = false;
        try {
            ByteBuffer pressed = org.lwjglx.input.Keyboard.sdlKeyPressedArray;
            if (pressed == null) {
                Lwjgl3ifyKeyboardDebug.report(key, false, "source=SDL-state-null");
                return false;
            }

            staticScancode = org.lwjglx.input.KeyCodes.lwjglToSdlScancode(key);
            if (staticScancode > 0 && staticScancode < pressed.limit()) {
                staticDown = pressed.get(staticScancode) != 0;
            }

            sdlKeycode = org.lwjglx.input.KeyCodes.lwjglToSdlKeycode(key);
            if (sdlKeycode != -1) {
                try {
                    Class<?> sdlKeyboard = Class.forName("org.lwjgl.sdl.SDLKeyboard");
                    java.lang.reflect.Method getScancode = sdlKeyboard.getMethod("SDL_GetScancodeFromKey", int.class, java.nio.ShortBuffer.class);
                    layoutScancode = (Integer) getScancode.invoke(null, sdlKeycode, null);
                } catch (Throwable t) {
                    try {
                        Class<?> sdlKeyboardX = Class.forName("org.lwjglx.sdl.SDLKeyboard");
                        java.lang.reflect.Method getScancode = sdlKeyboardX.getMethod("SDL_GetScancodeFromKey", int.class, java.nio.ShortBuffer.class);
                        layoutScancode = (Integer) getScancode.invoke(null, sdlKeycode, null);
                    } catch (Throwable t2) {
                        // ignore
                    }
                }
                
                if (layoutScancode > 0 && layoutScancode < pressed.limit()) {
                    layoutDown = pressed.get(layoutScancode) != 0;
                }
            }

            boolean result = staticDown || layoutDown;
            Lwjgl3ifyKeyboardDebug.report(key, result,
                    "source=" + (staticDown ? "static-scancode" : layoutDown ? "layout-scancode" : "none")
                            + " staticScan=" + staticScancode + " staticDown=" + staticDown
                            + " sdlKey=" + sdlKeycode + " layoutScan=" + layoutScancode
                            + " layoutDown=" + layoutDown);
            return result;
        } catch (Throwable error) {
            Lwjgl3ifyKeyboardDebug.report(key, false,
                    "source=exception type=" + error.getClass().getSimpleName()
                            + " message=" + String.valueOf(error.getMessage())
                            + " staticScan=" + staticScancode + " staticDown=" + staticDown
                            + " sdlKey=" + sdlKeycode + " layoutScan=" + layoutScancode
                            + " layoutDown=" + layoutDown);
            return false;
        }
    }
}
