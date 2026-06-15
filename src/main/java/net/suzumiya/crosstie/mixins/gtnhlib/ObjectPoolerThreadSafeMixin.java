package net.suzumiya.crosstie.mixins.gtnhlib;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Supplier;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * ObjectPoolerにスレッドセーフ性を追加するMixin。
 *
 * <p>
 * GTNHLib 0.10.0 では内部フィールド {@code availableInstances} の型が
 * {@code List<T>} から {@code ObjectArrayList<T>} (fastutil) に変更されました。
 * {@code @Accessor} の戻り値型をそれに合わせて更新しています。
 *
 * <p>
 * 配列一括追加には GTNHLib 0.11.12 と同様に
 * {@code ObjectArrayList.addElements()} を使用することで
 * 不要なラッパー生成を回避しています ({@link java.util.Arrays#asList} 不使用)。
 */
@Mixin(targets = "com.gtnewhorizon.gtnhlib.util.ObjectPooler", remap = false)
public abstract class ObjectPoolerThreadSafeMixin<T> {

    @Overwrite
    public synchronized T getInstance() {
        ObjectArrayList<T> pool = crosstie$getAvailableInstances();
        if (pool.isEmpty()) {
            return crosstie$getInstanceSupplier().get();
        }
        return pool.remove(pool.size() - 1);
    }

    @Overwrite
    public synchronized void releaseInstance(T instance) {
        if (instance == null) {
            return;
        }
        crosstie$getAvailableInstances().add(instance);
    }

    @Overwrite
    public synchronized void releaseInstances(Collection<T> instances) {
        ObjectArrayList<T> pool = crosstie$getAvailableInstances();
        for (T instance : instances) {
            if (instance != null) {
                pool.add(instance);
            }
        }
        instances.clear();
    }

    /**
     * GTNHLib 0.11.12 と同様に {@code ObjectArrayList.addElements()} を使用して
     * 配列要素を一括追加します。不要なラッパーオブジェクトを生成しません。
     * 参照: GTNHLib-0.11.12 ObjectPooler.java#L42
     */
    @Overwrite
    public synchronized void releaseInstances(T[] instances) {
        ObjectArrayList<T> pool = crosstie$getAvailableInstances();
        pool.addElements(pool.size(), instances);
        Arrays.fill(instances, null);
    }

    @Accessor(value = "availableInstances", remap = false)
    protected abstract ObjectArrayList<T> crosstie$getAvailableInstances();

    @Accessor(value = "instanceSupplier", remap = false)
    protected abstract Supplier<T> crosstie$getInstanceSupplier();
}