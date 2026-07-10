package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.modelpack.cfg.TrainConfig;
import net.suzumiya.crosstie.utils.ATSAssistReflectionHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityTrainBase.class, remap = false)
public abstract class EntityTrainBaseMixin {
    @Shadow public abstract int getNotch();
    @Shadow public abstract void setNotch(int notch);
    @Shadow public abstract float getSpeed();

    @Inject(method = "updateSpeed", at = @At("HEAD"), remap = false)
    private void onUpdateSpeedHead(CallbackInfo ci) {
        EntityTrainBase train = (EntityTrainBase) (Object) this;
        Object trainController = ATSAssistReflectionHelper.getTrainController(train);
        if (trainController == null) return;

        Object tasc = ATSAssistReflectionHelper.getTASCController(trainController);
        if (tasc == null || !ATSAssistReflectionHelper.isTASCEnabled(tasc)) return;

        boolean isBreaking = ATSAssistReflectionHelper.isBreaking(tasc);
        double stopDistance = ATSAssistReflectionHelper.getStopDistance(tasc);
        float speedH = this.getSpeed() * 72f;

        TrainConfig cfg = train.getModelSet().getConfig();
        if (cfg == null || cfg.deccelerations == null || cfg.deccelerations.length < 2) return;

        float[] decels = cfg.deccelerations;
        double[] decelsMS2 = new double[decels.length];
        for (int i = 0; i < decels.length; i++) {
            decelsMS2[i] = -decels[i] * 400.0;
        }
        int maxCommon = Math.max(1, decels.length - 2);

        if (isBreaking) {
            if (ATSAssistReflectionHelper.isStopPosition(tasc)) {
                int holdBrake = -Math.min(5, maxCommon);
                this.setNotch(holdBrake);
            } else if (stopDistance > 0) {
                double speedMS = speedH / 3.6;
                double reqDecel = (speedMS * speedMS) / (2.0 * stopDistance);
                
                int selectedNotch = 1;
                for (int i = 1; i <= maxCommon; i++) {
                    if (decelsMS2[i] >= reqDecel) {
                        selectedNotch = i;
                        break;
                    }
                    selectedNotch = i;
                }
                
                int serviceMax = Math.min(5, maxCommon);
                if (selectedNotch > serviceMax) {
                    selectedNotch = serviceMax;
                }
                this.setNotch(-selectedNotch);
            }
        } else {
            if (stopDistance > 0 && !ATSAssistReflectionHelper.isStopPosition(tasc)) {
                double speedMS = speedH / 3.6;
                double reqDecel = (speedMS * speedMS) / (2.0 * stopDistance);
                
                double b1Decel = decelsMS2[1];
                double startBrakeDecel = b1Decel * 0.8;
                startBrakeDecel = Math.max(0.10, Math.min(0.30, startBrakeDecel));
                
                if (reqDecel >= startBrakeDecel) {
                    ATSAssistReflectionHelper.setBraking(tasc, true);
                    
                    int selectedNotch = 1;
                    for (int i = 1; i <= maxCommon; i++) {
                        if (decelsMS2[i] >= reqDecel) {
                            selectedNotch = i;
                            break;
                        }
                        selectedNotch = i;
                    }
                    int serviceMax = Math.min(5, maxCommon);
                    if (selectedNotch > serviceMax) {
                        selectedNotch = serviceMax;
                    }
                    this.setNotch(-selectedNotch);
                }
            }
        }
    }
}
