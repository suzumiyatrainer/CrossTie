package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.train.parts.EntityVehiclePart;
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Phase 4-1: {@link EntityVehiclePart}（座席 {@code EntityFloor} 等）の停車中更新を最適化する。
 *
 * <p>親車両が完全静止（速度 0 かつ位置・姿勢変化なし）かつ非搭乗・非独立のパーツでは、
 * 毎 tick の {@code updatePartPos()}（3 軸回転計算＋{@code setPosition}）をスキップする。
 * {@code super.onUpdate()} はそのまま実行されるため {@code prevPos*} や {@code ticksExisted} は正常に維持される。</p>
 */
@SuppressWarnings("rawtypes")
@Mixin(value = EntityVehiclePart.class, remap = true)
public abstract class EntityVehiclePartOptimizationMixin {

    @Shadow(remap = false)
    public boolean needsUpdatePos;

    @Unique
    private EntityVehicleBase crosstie$lastVehicle;

    @Unique
    private double crosstie$lastVX = Double.NaN;

    @Unique
    private double crosstie$lastVY;

    @Unique
    private double crosstie$lastVZ;

    @Unique
    private float crosstie$lastYaw;

    @Unique
    private float crosstie$lastPitch;

    @Unique
    private float crosstie$lastRoll;

    @Unique
    private boolean crosstie$partPosDirty = true;

    @Inject(method = "setPartPos", at = @At("TAIL"), remap = false)
    private void crosstie$onSetPartPos(float x, float y, float z, CallbackInfo ci) {
        this.crosstie$partPosDirty = true;
    }

    @Inject(method = "setPositionAndRotation2", at = @At("TAIL"))
    private void crosstie$onSetPositionAndRotation2(double x, double y, double z, float yaw, float pitch, int incr, CallbackInfo ci) {
        this.crosstie$partPosDirty = true;
    }

    @Redirect(
        method = "onUpdate",
        at = @At(
            value = "INVOKE",
            target = "Ljp/ngt/rtm/entity/train/parts/EntityVehiclePart;updatePartPos(Ljp/ngt/rtm/entity/vehicle/EntityVehicleBase;)V",
            remap = false
        )
    )
    private void crosstie$skipStationaryUpdatePartPos(EntityVehiclePart self, EntityVehicleBase vehicle) {
        if (!this.crosstie$partPosDirty
                && !this.needsUpdatePos
                && self.riddenByEntity == null
                && vehicle == this.crosstie$lastVehicle
                && vehicle.getSpeed() == 0.0F
                && vehicle.posX == this.crosstie$lastVX
                && vehicle.posY == this.crosstie$lastVY
                && vehicle.posZ == this.crosstie$lastVZ
                && vehicle.rotationYaw == this.crosstie$lastYaw
                && vehicle.rotationPitch == this.crosstie$lastPitch
                && vehicle.rotationRoll == this.crosstie$lastRoll) {
            return;
        }

        self.updatePartPos(vehicle);
        this.crosstie$lastVehicle = vehicle;
        this.crosstie$lastVX = vehicle.posX;
        this.crosstie$lastVY = vehicle.posY;
        this.crosstie$lastVZ = vehicle.posZ;
        this.crosstie$lastYaw = vehicle.rotationYaw;
        this.crosstie$lastPitch = vehicle.rotationPitch;
        this.crosstie$lastRoll = vehicle.rotationRoll;
        this.crosstie$partPosDirty = false;
    }
}
