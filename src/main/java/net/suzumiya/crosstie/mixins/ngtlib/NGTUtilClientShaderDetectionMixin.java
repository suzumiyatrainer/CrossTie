package net.suzumiya.crosstie.mixins.ngtlib;

import java.lang.reflect.Method;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Extends legacy shader detection to Angelica/Iris shader packs without depending on either API at compile time.
 */
@Mixin(targets = "jp.ngt.ngtlib.util.NGTUtilClient", remap = false)
public abstract class NGTUtilClientShaderDetectionMixin {

    @Unique
    private static final String IRIS_API_CLASS = "net.irisshaders.iris.api.v0.IrisApi";
    @Unique
    private static final String IRIS_GET_INSTANCE = "getInstance";
    @Unique
    private static final String IRIS_IS_SHADER_PACK_IN_USE = "isShaderPackInUse";

    @Unique
    private static volatile byte crosstie$irisState = -1;
    @Unique
    private static volatile Method crosstie$irisGetInstance;
    @Unique
    private static volatile Method crosstie$irisIsShaderPackInUse;

    @Inject(method = "usingShader", at = @At("RETURN"), cancellable = true, remap = false)
    private static void crosstie$detectAngelicaIrisShaderPack(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue().booleanValue()) {
            return;
        }

        if (crosstie$isIrisShaderPackInUse()) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }

    @Unique
    private static boolean crosstie$isIrisShaderPackInUse() {
        if (!crosstie$initIrisApi()) {
            return false;
        }

        try {
            Object irisApi = crosstie$irisGetInstance.invoke(null);
            if (irisApi == null) {
                return false;
            }

            Object inUse = crosstie$irisIsShaderPackInUse.invoke(irisApi);
            return inUse instanceof Boolean && ((Boolean) inUse).booleanValue();
        } catch (ReflectiveOperationException | RuntimeException e) {
            crosstie$irisState = 0;
            crosstie$irisGetInstance = null;
            crosstie$irisIsShaderPackInUse = null;
            return false;
        }
    }

    @Unique
    private static boolean crosstie$initIrisApi() {
        if (crosstie$irisState >= 0) {
            return crosstie$irisState == 1;
        }

        synchronized (NGTUtilClientShaderDetectionMixin.class) {
            if (crosstie$irisState >= 0) {
                return crosstie$irisState == 1;
            }

            try {
                ClassLoader loader = NGTUtilClientShaderDetectionMixin.class.getClassLoader();
                Class<?> irisApiClass = Class.forName(IRIS_API_CLASS, false, loader);
                crosstie$irisGetInstance = irisApiClass.getMethod(IRIS_GET_INSTANCE);
                crosstie$irisIsShaderPackInUse = irisApiClass.getMethod(IRIS_IS_SHADER_PACK_IN_USE);
                crosstie$irisState = 1;
            } catch (ReflectiveOperationException | RuntimeException e) {
                crosstie$irisState = 0;
                crosstie$irisGetInstance = null;
                crosstie$irisIsShaderPackInUse = null;
            }

            return crosstie$irisState == 1;
        }
    }
}
