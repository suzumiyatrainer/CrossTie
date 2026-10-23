package net.suzumiya.crosstie.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @deprecated Use {@link WebCtcDirtyTracker} instead.
 */
@Deprecated
public class TrackOccupancyTracker {
    @Deprecated
    public static final Map<Long, Boolean> latestStates = new ConcurrentHashMap<>();
}
