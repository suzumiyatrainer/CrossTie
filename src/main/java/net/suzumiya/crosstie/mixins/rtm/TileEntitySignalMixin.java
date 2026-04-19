package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.tileentity.TileEntity;
import net.suzumiya.crosstie.CrossTie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "jp.ngt.rtm.electric.TileEntitySignal", remap = false)
public abstract class TileEntitySignalMixin extends TileEntity {

    @Inject(method = "getMaxRenderDistanceSquared", at = @At("RETURN"), cancellable = true, remap = false)
    private void crosstie$scaleSignalRenderDistance(CallbackInfoReturnable<Double> cir) {
        if (this.worldObj == null || !this.worldObj.isRemote) {
            return;
        }

        int renderChunks = CrossTie.proxy.getClientRenderDistance();
        if (renderChunks < 4) {
            renderChunks = 4;
        }

        double dist = renderChunks * 16.0D;
        double optimized = dist * dist;
        if (optimized > cir.getReturnValue().doubleValue()) {
            cir.setReturnValue(Double.valueOf(optimized));
        }
    }
}
