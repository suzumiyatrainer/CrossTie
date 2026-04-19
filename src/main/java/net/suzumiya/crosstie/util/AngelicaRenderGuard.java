package net.suzumiya.crosstie.util;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runtime safety guard for Angelica-side optimizations.
 * When rendering anomalies are detected, optimizations are temporarily disabled
 * and rendering falls back to the KaizPatchX-compatible path.
 */
public final class AngelicaRenderGuard {

    private static final int DEFAULT_FALLBACK_FRAMES = 240;
    private static final AtomicInteger FALLBACK_FRAMES = new AtomicInteger(0);

    private AngelicaRenderGuard() {
    }

    public static boolean isFallbackActive() {
        return FALLBACK_FRAMES.get() > 0;
    }

    public static void triggerFallback() {
        FALLBACK_FRAMES.set(DEFAULT_FALLBACK_FRAMES);
    }

    public static void triggerFallback(int frames) {
        if (frames <= 0) {
            frames = DEFAULT_FALLBACK_FRAMES;
        }
        FALLBACK_FRAMES.set(frames);
    }

    public static void tickFrame() {
        int current;
        do {
            current = FALLBACK_FRAMES.get();
            if (current <= 0) {
                return;
            }
        } while (!FALLBACK_FRAMES.compareAndSet(current, current - 1));
    }

    public static boolean hasInvalidFloat(float value) {
        return Float.isNaN(value) || Float.isInfinite(value);
    }

    public static boolean hasInvalidDouble(double value) {
        return Double.isNaN(value) || Double.isInfinite(value);
    }
}
