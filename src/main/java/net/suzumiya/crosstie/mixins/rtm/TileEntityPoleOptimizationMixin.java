package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.block.tileentity.TileEntityPole;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optimizes TileEntityPole by skipping updateEntity() once the model name is configured.
 * This avoids per-tick getBlockMetadata() and getBlockType() queries to the world,
 * which cause heavy overhead in maps with thousands of poles.
 */
@Mixin(value = TileEntityPole.class, remap = false)
public abstract class TileEntityPoleOptimizationMixin {

    @Inject(method = "updateEntity", at = @At("HEAD"), cancellable = true)
    private void crosstie$onUpdateEntity(CallbackInfo ci) {
        TileEntityPole self = (TileEntityPole) (Object) this;
        if (!self.getModelName().equals("")) {
            ci.cancel();
        }
    }
}
