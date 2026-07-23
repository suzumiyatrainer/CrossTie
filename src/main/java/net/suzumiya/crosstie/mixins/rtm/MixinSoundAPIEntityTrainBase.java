package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.common.FMLCommonHandler;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import net.suzumiya.crosstie.api.sound.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityTrainBase.class, remap = false)
public abstract class MixinSoundAPIEntityTrainBase {

    @Shadow public abstract int getNotch();

    @Unique
    private float crosstie$lastSpeed = -1f;

    @Inject(method = "setNotch", at = @At("RETURN"))
    private void crosstie$onSetNotch(int par1, CallbackInfoReturnable<Boolean> cir) {
        if (FMLCommonHandler.instance().getSide().isClient() && cir.getReturnValue()) {
            SoundManager.getInstance().onNotchChanged((EntityTrainBase)(Object)this, this.getNotch());
        }
    }

    @Inject(method = "setByteToDataWatcher", at = @At("RETURN"))
    private void crosstie$onStateChanged(int id, byte data, CallbackInfoReturnable<Byte> cir) {
        if (FMLCommonHandler.instance().getSide().isClient() && id == 4) { // TrainStateType.State_Door.id
            SoundManager.getInstance().onDoorStateChanged((EntityTrainBase)(Object)this, data);
        }
    }

    @Inject(method = "setSpeed_NoSync", at = @At("HEAD"))
    private void crosstie$onSpeedUpdate(float par1, CallbackInfo ci) {
        if (FMLCommonHandler.instance().getSide().isClient() && this.crosstie$lastSpeed != par1) {
            SoundManager.getInstance().onSpeedChanged((EntityTrainBase)(Object)this, par1);
            this.crosstie$lastSpeed = par1;
        }
    }
}
