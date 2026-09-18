package net.suzumiya.crosstie.utils.concurrent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Function;

/**
 * スレッドセーフな LRU キャッシュ。
 *
 * <p>GTNHLib の {@code ThreadsafeCache} を参考に CrossTie 内で純 Java 8 標準ライブラリのみで実装した版。
 * 外部ライブラリへの依存は一切ない。
 *
 * <p>読み取りは {@link StampedLock} の楽観的読み取り（競合なし時はロックコストゼロ）を利用し、
 * 書き込みのみ書き込みロックを取得する。
 *
 * @param <K> キーの型
 * @param <V> 値の型
 */
public class CrossTieStampedCache<K, V> {

    private final int maxSize;
    private final StampedLock lock = new StampedLock();
    private final LinkedHashMap<K, V> cache;
    private final Function<K, V> loader;
    private final boolean allowNulls;

    public CrossTieStampedCache(int maxSize, Function<K, V> loader, boolean allowNulls) {
        this.maxSize = maxSize;
        this.loader = loader;
        this.allowNulls = allowNulls;
        this.cache = new LinkedHashMap<K, V>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > CrossTieStampedCache.this.maxSize;
            }
        };
    }

    /**
     * キャッシュからエントリを取得する。存在しない場合は {@code loader} で生成してキャッシュに格納する。
     */
    public V get(K key) {
        // まず楽観的読み取りを試みる（ロックなし）
        long stamp = lock.tryOptimisticRead();
        V val = cache.get(key);
        if (lock.validate(stamp)) {
            if (val != null || (allowNulls && cache.containsKey(key))) {
                return val;
            }
        }

        // 楽観的読み取りが競合した、またはキャッシュミスの場合
        stamp = lock.readLock();
        try {
            val = cache.get(key);
            if (val != null || (allowNulls && cache.containsKey(key))) {
                return val;
            }
        } finally {
            lock.unlockRead(stamp);
        }

        // キャッシュミス → ローダーで生成して書き込み
        val = loader.apply(key);
        stamp = lock.writeLock();
        try {
            cache.put(key, val);
        } finally {
            lock.unlockWrite(stamp);
        }
        return val;
    }

    /**
     * キャッシュに値を直接格納する。
     */
    public void put(K key, V value) {
        long stamp = lock.writeLock();
        try {
            cache.put(key, value);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * キャッシュを全消去する（エポック進行時など）。
     */
    public void clear() {
        long stamp = lock.writeLock();
        try {
            cache.clear();
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * キャッシュにキーが存在するかどうかを確認する。
     */
    public boolean containsKey(K key) {
        long stamp = lock.tryOptimisticRead();
        boolean contains = cache.containsKey(key);
        if (lock.validate(stamp)) {
            return contains;
        }
        stamp = lock.readLock();
        try {
            return cache.containsKey(key);
        } finally {
            lock.unlockRead(stamp);
        }
    }
}
