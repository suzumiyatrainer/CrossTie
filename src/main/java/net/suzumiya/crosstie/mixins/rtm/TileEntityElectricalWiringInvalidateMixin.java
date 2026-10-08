package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.electric.TileEntityElectricalWiring;
import jp.ngt.rtm.electric.ElectricalWiringManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TileEntityElectricalWiring.class, remap = false)
public class TileEntityElectricalWiringInvalidateMixin {

    @Unique
    private boolean crosstie$isUnloading = false;

    @Inject(method = "onChunkUnload", at = @At("HEAD"))
    private void crosstie$onChunkUnload(CallbackInfo ci) {
        this.crosstie$isUnloading = true;
    }

    @Redirect(method = "invalidate", at = @At(value = "INVOKE", target = "Ljp/ngt/rtm/electric/ElectricalWiringManager;onNodeRemoved(III)V"))
    private void crosstie$redirectOnNodeRemoved(ElectricalWiringManager manager, int x, int y, int z) {
        if (!this.crosstie$isUnloading) {
            manager.onNodeRemoved(x, y, z);
        }
    }
}
