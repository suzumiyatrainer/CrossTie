package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.train.EntityTrainBase;
import net.suzumiya.crosstie.CrossTieConfig;
import net.suzumiya.crosstie.utils.TrainStandingHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets={"jp.ngt.rtm.entity.train.EntityTrainBase"}, remap=false)
public abstract class TrainStandingMixin {
    @Inject(method={"onVehicleUpdate"}, at={@At(value="INVOKE", target="Ljp/ngt/rtm/entity/vehicle/EntityVehicleBase;onVehicleUpdate()V", shift=At.Shift.AFTER, remap=false)}, remap=false)
    private void crosstie$updateStanding(CallbackInfo ci) {
        if (!CrossTieConfig.standingRoomEnabled) {
            return;
        }
        EntityTrainBase train = (EntityTrainBase)(Object)this;
        TrainStandingHandler.updateStandingEntities(train);
    }
}
