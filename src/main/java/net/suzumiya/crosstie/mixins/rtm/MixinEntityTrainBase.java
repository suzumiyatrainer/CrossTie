package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.train.EntityTrainBase;
import net.suzumiya.crosstie.util.TrainSpatialTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityTrainBase.class, remap = false)
public abstract class MixinEntityTrainBase {

    @Inject(method = "onUpdate", at = @At("TAIL"))
    private void crosstie$updateTrainSpatialIndex(CallbackInfo ci) {
        TrainSpatialTracker.updateTrain((EntityTrainBase) (Object) this);
    }

    @Inject(method = "setDead", at = @At("HEAD"))
    private void crosstie$removeTrainSpatialIndex(CallbackInfo ci) {
        TrainSpatialTracker.removeTrain((EntityTrainBase) (Object) this);
    }
}
