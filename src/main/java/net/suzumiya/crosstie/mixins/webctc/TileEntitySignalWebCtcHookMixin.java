package net.suzumiya.crosstie.mixins.webctc;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import jp.ngt.rtm.electric.TileEntitySignal;
import net.minecraft.tileentity.TileEntity;
import net.suzumiya.crosstie.utils.WebCtcDirtyTracker;

@Mixin(value = TileEntitySignal.class, remap = false)
public abstract class TileEntitySignalWebCtcHookMixin extends TileEntity {

    @Unique
    private boolean crosstie$initialTickDone = false;

    @Inject(method = "updateEntity", at = @At("HEAD"))
    private void crosstie$onUpdateEntityHead(CallbackInfo ci) {
        if (this.worldObj != null && !this.worldObj.isRemote) {
            if (!this.crosstie$initialTickDone) {
                this.crosstie$initialTickDone = true;
                WebCtcDirtyTracker.markSignalDirty((TileEntitySignal) (Object) this);
            }
        }
    }

    @Inject(method = "setElectricity", at = @At("RETURN"))
    private void crosstie$onSetElectricity(int x, int y, int z, int level, CallbackInfo ci) {
        if (this.worldObj != null && !this.worldObj.isRemote) {
            WebCtcDirtyTracker.markSignalDirty((TileEntitySignal) (Object) this);
        }
    }

    @Inject(method = "setOrigBlock", at = @At("HEAD"))
    private void crosstie$onSetOrigBlock(CallbackInfo ci) {
        if (this.worldObj != null && !this.worldObj.isRemote) {
            WebCtcDirtyTracker.markSignalRemoved(this.xCoord, this.yCoord, this.zCoord);
        }
    }

    @Inject(method = "invalidate", at = @At("HEAD"), remap = true)
    private void crosstie$onInvalidate(CallbackInfo ci) {
        if (this.worldObj != null && !this.worldObj.isRemote) {
            WebCtcDirtyTracker.markSignalRemoved(this.xCoord, this.yCoord, this.zCoord);
        }
    }
}
