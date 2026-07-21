package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.electric.TileEntityElectricalWiring;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optimizes TileEntityElectricalWiring updateEntity by checking if it is active.
 * Only processes logic on client-side when active (e.g. for particles during wiring).
 */
@Mixin(value = TileEntityElectricalWiring.class, remap = false)
public abstract class TileEntityEWUpdateOptimizationMixin {

    @Inject(method = "updateEntity", at = @At("HEAD"), cancellable = true)
    private void crosstie$onUpdateEntity(CallbackInfo ci) {
        TileEntityElectricalWiring self = (TileEntityElectricalWiring) (Object) this;
        if (self.getWorldObj() == null || !self.getWorldObj().isRemote || !self.isActivated) {
            ci.cancel();
        }
    }
}
