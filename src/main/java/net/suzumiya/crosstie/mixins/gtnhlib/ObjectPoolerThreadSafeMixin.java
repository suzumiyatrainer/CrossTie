package net.suzumiya.crosstie.mixins.gtnhlib;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * ObjectPoolerにスレッドセーフ性を追加するMixin。
 *
 * <p>GTNHLib 0.10.0 では内部フィールド {@code availableInstances} の型が
 * {@code List<T>} から {@code ObjectArrayList<T>} (fastutil) に変更されました。
 * {@code @Accessor} の戻り値型をそれに合わせて更新しています。
 * 配列一括追加には {@code ObjectArrayList.addElements()} を使用することで
 * 不要なラッパー生成を回避しています。
 */
@Mixin(targets = "com.gtnewhorizon.gtnhlib.util.ObjectPooler", remap = false)
public abstract class ObjectPoolerThreadSafeMixin<T> {

    @Overwrite
    public synchronized T getInstance() {
        List<T> pool = crosstie$getAvailableInstances();
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
        List<T> pool = crosstie$getAvailableInstances();
        for (T instance : instances) {
            if (instance != null) {
                pool.add(instance);
            }
        }
        instances.clear();
    }

    /**
     * GTNHLib 0.10.0 と同様に {@code ObjectArrayList.addElements()} を使用して
     * 配列要素を一括追加します。不要なラッパーオブジェクトを生成しません。
     */
    @Overwrite
    public synchronized void releaseInstances(T[] instances) {
        List<T> pool = crosstie$getAvailableInstances();
        pool.addAll(Arrays.asList(instances));
        Arrays.fill(instances, null);
    }

    @Accessor(value = "availableInstances", remap = false)
    protected abstract List<T> crosstie$getAvailableInstances();

    @Accessor(value = "instanceSupplier", remap = false)
    protected abstract Supplier<T> crosstie$getInstanceSupplier();
}
