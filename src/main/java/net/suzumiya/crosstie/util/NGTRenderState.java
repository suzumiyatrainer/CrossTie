package net.suzumiya.crosstie.util;

public class NGTRenderState {
    private static final ThreadLocal<Integer> renderDepth = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return 0;
        }
    };

    public static boolean isRendering() {
        return renderDepth.get() > 0;
    }

    public static void pushRendering() {
        renderDepth.set(renderDepth.get() + 1);
    }

    public static void popRendering() {
        int depth = renderDepth.get();
        if (depth > 0) {
            renderDepth.set(depth - 1);
        }
    }

    @Deprecated
    public static void setRendering(boolean state) {
        if (state) {
            pushRendering();
        } else {
            popRendering();
        }
    }
}
