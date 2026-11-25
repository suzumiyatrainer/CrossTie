package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import net.suzumiya.crosstie.CrossTieConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * [実験的] 連結車両走行時のめり込み防止（クライアント側・遅延ガード）：
 * ネットワークジッター等により特定車両の {@code PacketVehicleMovement} が 1〜2 tick 遅れ、
 * {@code vehiclePosRotationInc == 0} となって一瞬だけ完全停止してしまう現象を防止する。
 *
 * <p>
 * 直前の正常な 1 tick 移動ベクトルを記録しておき、走行中に補間ステップが枯渇した場合のみ
 * 最大 2 tick まで滑らかに外挿（coast）することで、高速走行時の突発的な車間縮小（めり込み）を抑制する。
 * 次のパケット到着時には通常の {@code vehiclePosRotationInc = 3} 補間へ自動的に収束する。
 * </p>
 *
 * <p>
 * {@link CrossTieConfig#fixConnectedTrainClipping} が {@code true} の場合のみ動作する。
 * </p>
 */
@Mixin(value = EntityVehicleBase.class, remap = false)
public abstract class EntityVehicleBaseSyncInterpMixin {

    @Shadow
    protected int vehiclePosRotationInc;

    @Unique
    private static final int CROSSTIE_MAX_COAST_TICKS = 2;

    @Unique
    private double crosstie$stepStartX;
    @Unique
    private double crosstie$stepStartY;
    @Unique
    private double crosstie$stepStartZ;
    @Unique
    private boolean crosstie$trackingStep = false;

    @Unique
    private double crosstie$lastStepX;
    @Unique
    private double crosstie$lastStepY;
    @Unique
    private double crosstie$lastStepZ;
    @Unique
    private boolean crosstie$hasLastStep = false;
    @Unique
    private int crosstie$coastTicks = 0;

    @Inject(method = "updatePosAndRotationClient", at = @At("HEAD"), require = 0, remap = false)
    private void crosstie$beforeClientInterp(CallbackInfo ci) {
        this.crosstie$trackingStep = false;
        if (!CrossTieConfig.fixConnectedTrainClipping) {
            this.crosstie$hasLastStep = false;
            this.crosstie$coastTicks = 0;
            return;
        }

        EntityVehicleBase<?> self = (EntityVehicleBase<?>) (Object) this;
        if (!(self instanceof EntityTrainBase)) {
            return;
        }

        if (this.vehiclePosRotationInc > 0) {
            this.crosstie$stepStartX = self.posX;
            this.crosstie$stepStartY = self.posY;
            this.crosstie$stepStartZ = self.posZ;
            this.crosstie$trackingStep = true;
            this.crosstie$coastTicks = 0;
        } else {
            float absSpeed = Math.abs(self.getSpeed());
            if (this.crosstie$hasLastStep && this.crosstie$coastTicks < CROSSTIE_MAX_COAST_TICKS && absSpeed > 0.01F) {
                double stepSq = this.crosstie$lastStepX * this.crosstie$lastStepX
                        + this.crosstie$lastStepY * this.crosstie$lastStepY
                        + this.crosstie$lastStepZ * this.crosstie$lastStepZ;
                double maxAllowed = (double) absSpeed * 1.5D + 0.1D;
                if (stepSq > 1.0E-8D && stepSq <= maxAllowed * maxAllowed) {
                    self.posX += this.crosstie$lastStepX;
                    self.posY += this.crosstie$lastStepY;
                    self.posZ += this.crosstie$lastStepZ;
                    ++this.crosstie$coastTicks;
                    return;
                }
            }
            this.crosstie$hasLastStep = false;
        }
    }

    @Inject(method = "updatePosAndRotationClient", at = @At("RETURN"), require = 0, remap = false)
    private void crosstie$afterClientInterp(CallbackInfo ci) {
        if (!this.crosstie$trackingStep) {
            return;
        }
        this.crosstie$trackingStep = false;

        EntityVehicleBase<?> self = (EntityVehicleBase<?>) (Object) this;
        double dx = self.posX - this.crosstie$stepStartX;
        double dy = self.posY - this.crosstie$stepStartY;
        double dz = self.posZ - this.crosstie$stepStartZ;
        double stepSq = dx * dx + dy * dy + dz * dz;
        double maxReasonable = (double) Math.abs(self.getSpeed()) * 2.5D + 0.5D;
        if (stepSq > 1.0E-8D && stepSq <= maxReasonable * maxReasonable) {
            this.crosstie$lastStepX = dx;
            this.crosstie$lastStepY = dy;
            this.crosstie$lastStepZ = dz;
            this.crosstie$hasLastStep = true;
        } else {
            this.crosstie$hasLastStep = false;
        }
    }
}
