package net.suzumiya.crosstie.cache;

import java.util.WeakHashMap;

public class ElectricalWiringCacheManager {
    /**
     * お飾り判定キャッシュ。
     * キー: TileEntityElectricalWiring（originノード）
     * 値: true = お飾り（BlockConnector等の機能接続なし）、false = 機能配線
     * WeakHashMapを使用してGCによる自動削除を許可する。
     */
    private static final WeakHashMap<Object, Boolean> DECORATIVE_CACHE = new WeakHashMap<>();

    public static Boolean get(Object tile) {
        return DECORATIVE_CACHE.get(tile);
    }

    public static void put(Object tile, boolean isDecorative) {
        DECORATIVE_CACHE.put(tile, isDecorative);
    }

    public static void clear() {
        DECORATIVE_CACHE.clear();
    }
}
