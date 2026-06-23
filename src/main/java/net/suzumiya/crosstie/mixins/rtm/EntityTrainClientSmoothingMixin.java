package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * クライアント側で電車の位置・回転を滑らかに補間し、マルチプレイのカクカクを緩和します。<br>
 * サーバーから受け取った位置を目標とし、毎tick線形補間（Lerp）で追従させます。
 */
@Mixin(targets = "jp.ngt.rtm.entity.train.EntityTrainBase", remap = false)
public abstract class EntityTrainClientSmoothingMixin {

    @Unique
    private double crosstie$targetPosX;
    @Unique
    private double crosstie$targetPosY;
    @Unique
    private double crosstie$targetPosZ;
    @Unique
    private float crosstie$targetYaw;
    @Unique
    private float crosstie$targetPitch;

    @Unique
    private boolean crosstie$hasTarget = false;

    @Unique
    private static final double LERP_POS = 0.18D;
    @Unique
    private static final double LERP_ROT = 0.22D;

    /**
     * サーバーから位置・回転が送られてきた時点で「目標位置」を記録します。
     */
    @Inject(method = "setPositionAndRotation", at = @At("RETURN"))
    private void crosstie$recordTarget(double x, double y, double z, float yaw, float pitch, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self.worldObj.isRemote) {
            crosstie$targetPosX = self.posX;
            crosstie$targetPosY = self.posY;
            crosstie$targetPosZ = self.posZ;
            crosstie$targetYaw = self.rotationYaw;
            crosstie$targetPitch = self.rotationPitch;
            crosstie$hasTarget = true;
        }
    }

    /**
     * 毎tick、現在の表示位置を目標位置に向けて滑らかに補間します。
     */
    @Inject(method = "onUpdate", at = @At("TAIL"))
    private void crosstie$applySmooth(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!self.worldObj.isRemote || !crosstie$hasTarget) {
            return;
        }

        double smoothX = self.posX + (crosstie$targetPosX - self.posX) * LERP_POS;
        double smoothY = self.posY + (crosstie$targetPosY - self.posY) * LERP_POS;
        double smoothZ = self.posZ + (crosstie$targetPosZ - self.posZ) * LERP_POS;
        float smoothYaw = self.rotationYaw + wrapTo180(crosstie$targetYaw - self.rotationYaw) * (float) LERP_ROT;
        float smoothPitch = self.rotationPitch + (crosstie$targetPitch - self.rotationPitch) * (float) LERP_ROT;

        self.setPositionAndRotation(smoothX, smoothY, smoothZ, smoothYaw, smoothPitch);
    }

    private static float wrapTo180(float value) {
        float wrapped = value % 360.0F;
        if (wrapped > 180.0F) wrapped -= 360.0F;
        if (wrapped < -180.0F) wrapped += 360.0F;
        return wrapped;
    }
}