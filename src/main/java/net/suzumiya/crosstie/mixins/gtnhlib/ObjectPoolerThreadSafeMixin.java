package net.suzumiya.crosstie.mixins.gtnhlib;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(targets = "com.gtnewhorizon.gtnhlib.util.ObjectPooler", remap = false)
public abstract class ObjectPoolerThreadSafeMixin<T> {

    @Overwrite
    public synchronized T getInstance() {
        List<T> availableInstances = getAvailableInstances();
        if (availableInstances.isEmpty()) {
            return getInstanceSupplier().get();
        }
        return availableInstances.remove(availableInstances.size() - 1);
    }

    @Overwrite
    public synchronized void releaseInstance(T instance) {
        if (instance == null) {
            return;
        }
        getAvailableInstances().add(instance);
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
        getAvailableInstances().addAll(Arrays.asList(instances));
        Arrays.fill(instances, null);
    }

    @SuppressWarnings("unchecked")
    private List<T> getAvailableInstances() {
        return (List<T>) getFieldValue("availableInstances");
    }

    @SuppressWarnings("unchecked")
    private Supplier<T> getInstanceSupplier() {
        return (Supplier<T>) getFieldValue("instanceSupplier");
    }

    private Object getFieldValue(String fieldName) {
        try {
            java.lang.reflect.Field field = this.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(this);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to access GTNHLib ObjectPooler field " + fieldName, e);
        }
    }
}
