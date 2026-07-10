package net.suzumiya.crosstie.compat;

public class DiscordSRVCompat {

    /**
     * Prevents NullPointerException when the target class is null.
     * Used by ASM patching in CrossTieClassTransformer to patch
     * DiscordSRV's NMSUtil.getTexture().
     */
    public static boolean safeIsInstance(Class<?> clazz, Object obj) {
        if (clazz == null) {
            return false;
        }
        return clazz.isInstance(obj);
    }
}
