package net.suzumiya.crosstie.mixins.signal;

import java.lang.reflect.Field;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "jp.masa.signalcontrollermod.block.tileentity.TileEntitySignalController", remap = false)
public abstract class SignalControllerReflectionCacheMixin {

    private static volatile Field crosstie$signalLevelField;

    @Redirect(
            method = "getSignal",
            at = @At(
                    value = "INVOKE",
                    target = "Ljp/ngt/ngtlib/util/NGTUtil;getField(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;",
                    remap = false),
            require = 0,
            remap = false)
    private Object crosstie$getCachedSignalLevel(Class<?> owner, Object instance, String fieldName) {
        if (instance == null || owner == null) {
            return null;
        }

        if ("signalLevel".equals(fieldName)) {
            try {
                Field field = crosstie$signalLevelField;
                if (field == null) {
                    field = crosstie$findField(owner, fieldName);
                    field.setAccessible(true);
                    crosstie$signalLevelField = field;
                }
                return field.get(instance);
            } catch (ReflectiveOperationException ignored) {
                return crosstie$getFieldReflectively(owner, instance, fieldName);
            }
        }

        return crosstie$getFieldReflectively(owner, instance, fieldName);
    }

    private static Object crosstie$getFieldReflectively(Class<?> owner, Object instance, String fieldName) {
        try {
            Field field = crosstie$findField(owner, fieldName);
            field.setAccessible(true);
            return field.get(instance);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Field crosstie$findField(Class<?> owner, String fieldName) throws NoSuchFieldException {
        Class<?> type = owner;
        while (type != null) {
            try {
                return type.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}
