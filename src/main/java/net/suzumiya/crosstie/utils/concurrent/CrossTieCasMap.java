package net.suzumiya.crosstie.utils.concurrent;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * 読み取りがロックコストゼロの並行マップ。
 *
 * <p>GTNHLib の {@code CasMap / CasAdapter} を参考に CrossTie 内で純 Java 8 標準ライブラリのみで実装した版。
 * 外部ライブラリへの依存は一切ない。
 *
 * <p>実装方式:
 * <ul>
 *   <li>読み取り: {@link AtomicReference#get()} のみ — ロックコストゼロ</li>
 *   <li>書き込み: {@code synchronized} ブロック内で HashMap をコピーして差し替え (Copy-on-Write)</li>
 * </ul>
 *
 * <p>「読み取りが圧倒的に多く、書き込みは稀」なユースケース ({@code FormationManager} 等) に最適。
 *
 * @param <K> キーの型
 * @param <V> 値の型
 */
public class CrossTieCasMap<K, V> implements Map<K, V> {

    private final AtomicReference<Map<K, V>> ref;

    public CrossTieCasMap() {
        this.ref = new AtomicReference<>(Collections.emptyMap());
    }

    // ========== 読み取り操作 (ロックゼロ) ==========

    private Map<K, V> snapshot() {
        return ref.get();
    }

    @Override
    public V get(Object key) {
        return snapshot().get(key);
    }

    @Override
    public boolean containsKey(Object key) {
        return snapshot().containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return snapshot().containsValue(value);
    }

    @Override
    public int size() {
        return snapshot().size();
    }

    @Override
    public boolean isEmpty() {
        return snapshot().isEmpty();
    }

    @Override
    public Set<K> keySet() {
        return snapshot().keySet();
    }

    @Override
    public Collection<V> values() {
        return snapshot().values();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return snapshot().entrySet();
    }

    // ========== 書き込み操作 (Copy-on-Write) ==========

    private <R> R mutate(Function<HashMap<K, V>, R> mutator) {
        synchronized (this) {
            HashMap<K, V> mutable = new HashMap<>(ref.get());
            R result = mutator.apply(mutable);
            ref.set(Collections.unmodifiableMap(mutable));
            return result;
        }
    }

    @Override
    public V put(K key, V value) {
        return mutate(m -> m.put(key, value));
    }

    @Override
    public V remove(Object key) {
        return mutate(m -> m.remove(key));
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> other) {
        mutate(m -> {
            m.putAll(other);
            return null;
        });
    }

    @Override
    public void clear() {
        mutate(m -> {
            m.clear();
            return null;
        });
    }
}
