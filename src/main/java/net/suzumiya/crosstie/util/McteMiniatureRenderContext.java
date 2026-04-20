package net.suzumiya.crosstie.util;

/**
 * Tracks MCTE miniature rendering while CrossTie bypasses Angelica's display-list recorder.
 */
public final class McteMiniatureRenderContext {

    private static final int ACTIVE_DEPTH = 0;
    private static final int USING_LEGACY_DISPLAY_LIST = 1;

    private static final ThreadLocal<int[]> STATE = new ThreadLocal<int[]>() {
        @Override
        protected int[] initialValue() {
            return new int[] { 0, 0 };
        }
    };

    private McteMiniatureRenderContext() {
    }

    public static boolean isActive() {
        return STATE.get()[ACTIVE_DEPTH] > 0;
    }

    public static void enter() {
        int[] state = STATE.get();
        state[ACTIVE_DEPTH]++;
        state[USING_LEGACY_DISPLAY_LIST] = 0;
    }

    public static void exit() {
        int[] state = STATE.get();
        if (state[ACTIVE_DEPTH] > 0) {
            state[ACTIVE_DEPTH]--;
        }
        if (state[ACTIVE_DEPTH] == 0) {
            state[USING_LEGACY_DISPLAY_LIST] = 0;
        }
    }

    public static boolean isUsingLegacyDisplayList() {
        return STATE.get()[USING_LEGACY_DISPLAY_LIST] != 0;
    }

    public static void setUsingLegacyDisplayList(boolean using) {
        STATE.get()[USING_LEGACY_DISPLAY_LIST] = using ? 1 : 0;
    }

    public static void reset() {
        STATE.remove();
    }
}
