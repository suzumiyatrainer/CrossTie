package net.suzumiya.crosstie.mixins.webctc;

import net.suzumiya.crosstie.config.CrossTieConfig;
import net.suzumiya.crosstie.util.ReflectionFieldCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Field;

@Mixin(targets = "org.webctc.railgroup.RailGroupUtilsKt", remap = false)
public abstract class RailGroupUtilsMixin {

    @Redirect(
            method = "getSignalLevel",
            at = @At(value = "INVOKE", target = "Ljava/lang/Class;getFields()[Ljava/lang/reflect/Field;"))
    private static Field[] crosstie$getCachedSignalFields(Class<?> owner) {
        if (CrossTieConfig.enableWebCTCReflectionCache) {
            return ReflectionFieldCache.getPublicFields(owner);
        }
        return owner.getFields();
    }
}
