package net.suzumiya.crosstie.compat.lwjgl3ify;

/** Keyboard.isKeyDown bridge used by scripts when lwjgl3ify is present. */
public final class KeyboardAdapter {
    private KeyboardAdapter() {
    }

    public static boolean isKeyDown(int key) {
        return Lwjgl3ifyKeyboardCompat.mergeIsKeyDownResult(key, false);
    }
}
