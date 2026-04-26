package net.suzumiya.crosstie.mixins.signalcontrollermod;

import net.suzumiya.crosstie.config.CrossTieConfig;
import net.suzumiya.crosstie.util.ReflectionFieldCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "jp.masa.signalcontrollermod.block.tileentity.TileEntitySignalController", remap = false)
public abstract class TileEntitySignalControllerMixin {

    @Redirect(
            method = "getSignal",
            at = @At(value = "INVOKE", target = "Ljp/ngt/ngtlib/util/NGTUtil;getField(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;"),
            remap = false)
    private Object crosstie$getSignalLevelWithCachedField(Class<?> owner, Object instance, String name) {
        if (CrossTieConfig.enableSignalReflectionCache && "signalLevel".equals(name)) {
            return ReflectionFieldCache.getDeclaredFieldValue(owner, instance, name);
        }
        return crosstie$getFieldSlow(owner, instance, name);
    }

    private static Object crosstie$getFieldSlow(Class<?> owner, Object instance, String name) {
        try {
            java.lang.reflect.Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(instance);
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return null;
        }
    }
}

