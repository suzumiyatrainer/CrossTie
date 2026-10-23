package net.suzumiya.crosstie.mixins.webctc;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import jp.ngt.rtm.electric.TileEntitySignal;

@Mixin(targets = "org.webctc.railgroup.RailGroupUtilsKt", remap = false)
public abstract class RailGroupUtilsSignalLevelMixin {

    /**
     * WebCTC 1.2.0: RailGroupUtilsKt#getSignalLevel(TileEntitySignal) は、
     * TileEntitySignal.class.getFields() を全件走査して "signalLevel" を探すリフレクションを行っており、
     * 毎 tick 大量のアロケーションとCPU時間を浪費している。
     * 直接 signal.getSignal() を返却して即座に完了させる。
     */
    @Inject(
            method = "getSignalLevel",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void crosstie$fastGetSignalLevel(TileEntitySignal signal, CallbackInfoReturnable<Integer> cir) {
        if (signal != null) {
            cir.setReturnValue(signal.getSignal());
        } else {
            cir.setReturnValue(0);
        }
    }
}
