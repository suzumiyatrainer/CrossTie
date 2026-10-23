package net.suzumiya.crosstie.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jp.ngt.rtm.electric.TileEntitySignal;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;

/**
 * WebCTC 差分更新（案2A）用トラッカー。
 * レールおよび信号機の状態変化（占有・形状・転轍機・信号現示）や破壊を検知し、
 * WebCTC の update() 実行時に変更があった TileEntity のみを提供することで
 * 毎 20Tick の全件走査（loadedTileEntityList）と不要なオブジェクト生成を削減する。
 */
public class WebCtcDirtyTracker {

    /** 変化があった LargeRailCore の集合 */
    private static final Set<TileEntityLargeRailCore> dirtyRails = Collections.newSetFromMap(new ConcurrentHashMap<TileEntityLargeRailCore, Boolean>());

    /** 破壊されたレールの座標集合 (パックド long: x, y, z) */
    private static final Set<Long> removedRails = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());

    /** 変化があった Signal の集合 */
    private static final Set<TileEntitySignal> dirtySignals = Collections.newSetFromMap(new ConcurrentHashMap<TileEntitySignal, Boolean>());

    /** 破壊された信号機の座標集合 (パックド long: x, y, z) */
    private static final Set<Long> removedSignals = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());

    /** サーバー起動後の初回スキャンが完了したか */
    private static volatile boolean initialRailScanDone = false;
    private static volatile boolean initialSignalScanDone = false;

    /**
     * 座標 (x, y, z) を 64-bit long にパックする。
     */
    public static long packPos(int x, int y, int z) {
        return (((long) x & 0x3FFFFFFL) << 38) | (((long) y & 0xFFFL) << 26) | ((long) z & 0x3FFFFFFL);
    }

    public static int unpackX(long packed) {
        long x = (packed >> 38) & 0x3FFFFFFL;
        if ((x & 0x2000000L) != 0) { // 符号ビット拡張
            x |= ~0x3FFFFFFL;
        }
        return (int) x;
    }

    public static int unpackY(long packed) {
        return (int) ((packed >> 26) & 0xFFFL);
    }

    public static int unpackZ(long packed) {
        long z = packed & 0x3FFFFFFL;
        if ((z & 0x2000000L) != 0) { // 符号ビット拡張
            z |= ~0x3FFFFFFL;
        }
        return (int) z;
    }

    // ---- レール関連 ---- //

    public static void markRailDirty(TileEntityLargeRailCore core) {
        if (core != null && !core.isInvalid()) {
            dirtyRails.add(core);
        }
    }

    public static void markRailRemoved(int x, int y, int z) {
        removedRails.add(packPos(x, y, z));
    }

    public static List<Long> pollRemovedRails() {
        if (removedRails.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> result;
        synchronized (removedRails) {
            result = new ArrayList<Long>(removedRails);
            removedRails.clear();
        }
        return result;
    }

    /**
     * WebCTC RailCacheData.update() 用:
     * 初回は全ロード済みリストを返し、2回目以降は Dirty なレールのみのリストを返す。
     */
    public static List<?> pollDirtyRailsOrAll(List<?> allLoaded) {
        if (!initialRailScanDone) {
            initialRailScanDone = true;
            return allLoaded;
        }

        if (dirtyRails.isEmpty()) {
            return Collections.emptyList();
        }

        List<TileEntityLargeRailCore> result;
        synchronized (dirtyRails) {
            result = new ArrayList<TileEntityLargeRailCore>(dirtyRails);
            dirtyRails.clear();
        }
        for (java.util.Iterator<TileEntityLargeRailCore> it = result.iterator(); it.hasNext();) {
            TileEntityLargeRailCore core = it.next();
            if (core == null || core.isInvalid()) {
                it.remove();
            }
        }
        return result;
    }

    // ---- 信号機関連 ---- //

    public static void markSignalDirty(TileEntitySignal signal) {
        if (signal != null && !signal.isInvalid()) {
            dirtySignals.add(signal);
        }
    }

    public static void markSignalRemoved(int x, int y, int z) {
        removedSignals.add(packPos(x, y, z));
    }

    public static List<Long> pollRemovedSignals() {
        if (removedSignals.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> result;
        synchronized (removedSignals) {
            result = new ArrayList<Long>(removedSignals);
            removedSignals.clear();
        }
        return result;
    }

    /**
     * WebCTC SignalCacheData.update() 用:
     * 初回は全ロード済みリストを返し、2回目以降は Dirty な信号機のみのリストを返す。
     */
    public static List<?> pollDirtySignalsOrAll(List<?> allLoaded) {
        if (!initialSignalScanDone) {
            initialSignalScanDone = true;
            return allLoaded;
        }

        if (dirtySignals.isEmpty()) {
            return Collections.emptyList();
        }

        List<TileEntitySignal> result;
        synchronized (dirtySignals) {
            result = new ArrayList<TileEntitySignal>(dirtySignals);
            dirtySignals.clear();
        }
        for (java.util.Iterator<TileEntitySignal> it = result.iterator(); it.hasNext();) {
            TileEntitySignal sig = it.next();
            if (sig == null || sig.isInvalid()) {
                it.remove();
            }
        }
        return result;
    }

    /**
     * ワールドアンロード時やリセット用
     */
    public static void reset() {
        dirtyRails.clear();
        removedRails.clear();
        dirtySignals.clear();
        removedSignals.clear();
        initialRailScanDone = false;
        initialSignalScanDone = false;
    }
}
