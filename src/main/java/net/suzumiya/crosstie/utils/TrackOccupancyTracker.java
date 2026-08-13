package net.suzumiya.crosstie.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.webctc.common.types.PosInt;

/**
 * Tracks the real-time track occupancy state of RTM rails for WebCTC optimization.
 * This is populated by TileEntityLargeRailCoreOccupancyMixin and consumed by RailCacheDataUpdateOptimizationMixin.
 */
public class TrackOccupancyTracker {
    public static final Map<PosInt, Boolean> latestStates = new ConcurrentHashMap<>();
}
