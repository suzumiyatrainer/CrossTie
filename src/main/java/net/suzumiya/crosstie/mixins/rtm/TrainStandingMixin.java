package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.train.EntityBogie;
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
        EntityTrainBase train = (EntityTrainBase)(Object)this;

        // クライアント側で台車のカント角から車体のRollを更新（RTM本体ではクライアント側での車体Roll計算が抜けていたため、台車は傾くのに車体が傾かない問題の修正）
        if (train.worldObj.isRemote && train.existBogies()) {
            EntityBogie bogie0 = train.getBogie(0);
            EntityBogie bogie1 = train.getBogie(1);
            if (bogie0 != null && bogie1 != null) {
                float roll = (bogie0.rotationRoll + -bogie1.rotationRoll) * 0.5F;
                train.updateRoll(roll);
            }
        }

        if (!CrossTieConfig.standingRoomEnabled) {
            return;
        }
        TrainStandingHandler.updateStandingEntities(train);
    }
}
