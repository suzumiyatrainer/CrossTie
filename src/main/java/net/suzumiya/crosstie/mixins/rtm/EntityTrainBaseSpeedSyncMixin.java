package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.Entity;
import net.suzumiya.crosstie.CrossTieConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reduces RTM train speed DataWatcher churn while keeping server-side logic exact.
 *
 * <p>RTM stores authoritative speed in {@code trainSpeed}, but {@code getSpeed()}
 * reads the DataWatcher. Since ATS logic can call {@code getSpeed()} on the server,
 * this mixin returns {@code trainSpeed} server-side and only throttles the networked
 * DataWatcher value used by clients.
 */
@Mixin(targets = "jp.ngt.rtm.entity.train.EntityTrainBase", remap = false)
public abstract class EntityTrainBaseSpeedSyncMixin {

    @Shadow
    private float trainSpeed;

    @Unique
    private static final int DW_SPEED = 25;

    @Unique
    private static final float SPEED_SYNC_THRESHOLD = 0.0025F;

    @Unique
    private static final int MAX_SPEED_SYNC_INTERVAL_TICKS = 20;

    @Unique
    private float crosstie$lastSyncedSpeed = Float.NaN;

    @Unique
    private int crosstie$ticksSinceSpeedSync = 0;

    @Inject(method = "getSpeed", at = @At("HEAD"), cancellable = true)
    private void crosstie$returnAuthoritativeServerSpeed(CallbackInfoReturnable<Float> cir) {
        if (!CrossTieConfig.trainSpeedSyncEnabled) {
            return;
        }
        Entity entity = (Entity) (Object) this;
        if (entity.worldObj != null && !entity.worldObj.isRemote) {
            cir.setReturnValue(this.trainSpeed);
        }
    }

    @Inject(method = "setSpeed_NoSync", at = @At("HEAD"), cancellable = true)
    private void crosstie$throttleSpeedSync(float newSpeed, CallbackInfo ci) {
        if (!CrossTieConfig.trainSpeedSyncEnabled) {
            return;
        }
        if (this.trainSpeed != newSpeed) {
            this.trainSpeed = newSpeed;
            this.crosstie$ticksSinceSpeedSync++;

            if (Float.isNaN(this.crosstie$lastSyncedSpeed)
                    || Math.abs(this.crosstie$lastSyncedSpeed - newSpeed) > SPEED_SYNC_THRESHOLD
                    || newSpeed == 0.0F
                    || this.crosstie$ticksSinceSpeedSync >= MAX_SPEED_SYNC_INTERVAL_TICKS) {
                DataWatcher dataWatcher = ((Entity) (Object) this).getDataWatcher();
                dataWatcher.updateObject(DW_SPEED, newSpeed);
                this.crosstie$lastSyncedSpeed = newSpeed;
                this.crosstie$ticksSinceSpeedSync = 0;
            }
        }
        ci.cancel();
    }
}
