package net.suzumiya.crosstie.mixins.webctc;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.suzumiya.crosstie.util.CrossTieDiagnostics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Speeds up WebCTC's TileEntitySignal.signalLevel Kotlin extension.
 *
 * <p>RailGroupUtilsKt#getSignalLevel currently calls {@code tile.javaClass.fields}
 * every time it reads a signal. Returning a pre-filtered cached Field array keeps
 * WebCTC's existing Kotlin logic intact while removing the repeated reflection scan.
 */
@Mixin(targets = "org.webctc.railgroup.RailGroupUtilsKt", remap = false)
public abstract class WebCtcSignalLevelCacheMixin {

    @Unique
    private static final Map<Class<?>, Field[]> crosstie$signalLevelFields = new ConcurrentHashMap<>();

    @Redirect(
            method = "getSignalLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Class;getFields()[Ljava/lang/reflect/Field;"),
            require = 0,
            remap = false)
    private static Field[] crosstie$getCachedSignalFields(Class<?> owner) {
        Field[] cached = crosstie$signalLevelFields.get(owner);
        if (cached != null) {
            if (CrossTieDiagnostics.isEnabled()) {
                CrossTieDiagnostics.reflectionCacheHits.incrementAndGet();
            }
            return cached;
        }

        Field signalLevel = crosstie$findPublicField(owner, "signalLevel");
        Field[] result = signalLevel == null ? owner.getFields() : new Field[] { signalLevel };
        crosstie$signalLevelFields.put(owner, result);
        if (CrossTieDiagnostics.isEnabled()) {
            CrossTieDiagnostics.reflectionCacheMisses.incrementAndGet();
        }
        return result;
    }

    @Unique
    private static Field crosstie$findPublicField(Class<?> owner, String name) {
        for (Field field : owner.getFields()) {
            if (name.equals(field.getName())) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }
}
