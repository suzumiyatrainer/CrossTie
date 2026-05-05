package net.suzumiya.crosstie.mixins.gtnhlib;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.gtnewhorizon.gtnhlib.util.ObjectPooler", remap = false)
public abstract class ObjectPoolerThreadSafeMixin<T> {

    @Overwrite
    public synchronized T getInstance() {
        List<T> availableInstances = crosstie$getAvailableInstances();
        if (availableInstances.isEmpty()) {
            return crosstie$getInstanceSupplier().get();
        }
        return availableInstances.remove(availableInstances.size() - 1);
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
        for (T instance : instances) {
            this.releaseInstance(instance);
        }
        instances.clear();
    }

    @Overwrite
    public synchronized void releaseInstances(T[] instances) {
        crosstie$getAvailableInstances().addAll(Arrays.asList(instances));
        Arrays.fill(instances, null);
    }

    @Accessor(value = "availableInstances", remap = false)
    protected abstract List<T> crosstie$getAvailableInstances();

    @Accessor(value = "instanceSupplier", remap = false)
    protected abstract Supplier<T> crosstie$getInstanceSupplier();
}
