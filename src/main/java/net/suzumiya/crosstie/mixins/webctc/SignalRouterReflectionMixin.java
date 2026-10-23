package net.suzumiya.crosstie.mixins.webctc;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import jp.ngt.rtm.electric.TileEntitySignal;

@Mixin(targets = "org.webctc.router.api.SignalRouterKt", remap = false)
public abstract class SignalRouterReflectionMixin {

    /**
     * WebCTC 1.2.0: toDataClass(TileEntitySignal) 内で signalLevel を取得する際に
     * NGTUtil.getField(TileEntitySignal.class, this, "signalLevel") という低速なリフレクションが
     * 毎 tick 全信号機に対して呼び出されている。
     * TileEntitySignal#getSignal() を直接呼び出すことでリフレクションを完全排除する。
     */
    @Redirect(
            method = "toDataClass",
            at = @At(
                    value = "INVOKE",
                    target = "Ljp/ngt/ngtlib/util/NGTUtil;getField(Ljava/lang/Class;Ljava/lang/Object;[Ljava/lang/String;)Ljava/lang/Object;",
                    remap = false
            ),
            remap = false
    )
    private static Object crosstie$fastGetSignalLevel(Class<?> clazz, Object instance, String[] names) {
        if (instance instanceof TileEntitySignal) {
            return ((TileEntitySignal) instance).getSignal();
        }
        return 0;
    }
}
