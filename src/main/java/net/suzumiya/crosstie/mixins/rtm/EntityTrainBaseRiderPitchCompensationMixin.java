package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.train.EntityTrainBase;
import net.minecraft.util.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RTM本体は運転士の rotationPitch を車体の勾配変化量だけ直接加算して追従させるが、
 * CrossTie では勾配/カントの視点追従を CameraTiltMixin で一元的に行う
 * （視線の向きに応じて Pitch/Roll を正しく分解するため）。
 * 二重適用を避けるため、RTM本体が引いたピッチ変化量をここで打ち消す。
 * ヨー(Yaw)の追従はRTM本体のものをそのまま使用する。
 */
@Mixin(targets = { "jp.ngt.rtm.entity.train.EntityTrainBase" }, remap = false)
public abstract class EntityTrainBaseRiderPitchCompensationMixin {

    @Inject(method = { "updateRiderPosition", "func_70043_V" }, at = @At("TAIL"), remap = false)
    private void crosstie$cancelRtmPitchFollow(CallbackInfo ci) {
        EntityTrainBase train = (EntityTrainBase) (Object) this;
        if (train.riddenByEntity != null) {
            float dPitch = MathHelper.wrapAngleTo180_float(train.rotationPitch - train.prevRotationPitch);
            if (dPitch != 0.0F) {
                train.riddenByEntity.rotationPitch += dPitch;
            }
        }
    }
}
