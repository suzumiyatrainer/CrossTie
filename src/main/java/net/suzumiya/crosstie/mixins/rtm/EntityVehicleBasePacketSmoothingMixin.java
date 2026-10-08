package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import net.suzumiya.crosstie.utils.TrainStandingHandler;
import net.minecraft.util.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;

/** Client-side interpolation of RTM vehicle tracking updates. */
@Mixin(value = EntityVehicleBase.class, remap = false)
public abstract class EntityVehicleBasePacketSmoothingMixin {

    @Unique
    private static final int crosstie$renderDelayTicks = 6;
    @Unique
    private static final int crosstie$maxSnapshots = 16;
    @Unique
    private static final double crosstie$offsetAlpha = 0.06D;
    @Unique
    private final ArrayDeque<crosstie$Snapshot> crosstie$snapshots = new ArrayDeque<>();
    @Unique
    private double crosstie$targetOffset = Double.NaN;
    @Unique
    private double crosstie$smoothedOffset = Double.NaN;

    @Unique
    private static final class crosstie$Snapshot {
        final long tick;
        final double x, y, z;
        final float yaw, pitch, roll;

        crosstie$Snapshot(long tick, double x, double y, double z, float yaw, float pitch, float roll) {
            this.tick = tick;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
        }
    }

    @SuppressWarnings("rawtypes")
    @Inject(method = { "setPositionAndRotation2(DDDFFI)V", "setPositionAndRotation2",
            "func_70056_a" }, at = @At("HEAD"), require = 0, remap = false)
    private void crosstie$captureTrackingPacket(double x, double y, double z, float yaw, float pitch, int increments,
            CallbackInfo ci) {
        EntityVehicleBase self = (EntityVehicleBase) (Object) this;
        long tick = self.ticksExisted;
        crosstie$Snapshot last = crosstie$snapshots.peekLast();
        if (last != null && tick < last.tick) {
            crosstie$snapshots.clear();
            crosstie$smoothedOffset = Double.NaN;
        }
        crosstie$snapshots.addLast(new crosstie$Snapshot(tick, x, y, z, yaw, pitch, self.getRoll()));
        while (crosstie$snapshots.size() > crosstie$maxSnapshots) {
            crosstie$snapshots.removeFirst();
        }
        crosstie$targetOffset = (double) tick - (double) self.ticksExisted;
        if (Double.isNaN(crosstie$smoothedOffset)) {
            crosstie$smoothedOffset = crosstie$targetOffset;
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Inject(method = { "updatePosAndRotationClient()V",
            "updatePosAndRotationClient" }, at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$interpolateTrackingPackets(CallbackInfo ci) {
        if (crosstie$snapshots.isEmpty() || Double.isNaN(crosstie$smoothedOffset)) {
            return;
        }

        EntityVehicleBase self = (EntityVehicleBase) (Object) this;
        if (self == TrainStandingHandler.getClientStandingTrain()) {
            // Keep the standing player and its train on the same client timeline.
            crosstie$snapshots.clear();
            crosstie$targetOffset = Double.NaN;
            crosstie$smoothedOffset = Double.NaN;
            return;
        }
        if (!Double.isNaN(crosstie$targetOffset)) {
            crosstie$smoothedOffset += (crosstie$targetOffset - crosstie$smoothedOffset) * crosstie$offsetAlpha;
        }
        double renderTick = (double) self.ticksExisted + crosstie$smoothedOffset - crosstie$renderDelayTicks;
        crosstie$Snapshot before = null;
        crosstie$Snapshot after = null;
        for (crosstie$Snapshot snapshot : crosstie$snapshots) {
            if (snapshot.tick <= renderTick) {
                before = snapshot;
            } else {
                after = snapshot;
                break;
            }
        }

        if (before == null) {
            // Retain vanilla tracking until enough packet history exists for the delay.
            return;
        }
        if (after != null && after.tick > before.tick) {
            float t = (float) ((renderTick - before.tick) / (double) (after.tick - before.tick));
            t = MathHelper.clamp_float(t, 0.0F, 1.0F);
            self.posX = before.x + (after.x - before.x) * t;
            self.posY = before.y + (after.y - before.y) * t;
            self.posZ = before.z + (after.z - before.z) * t;
            self.rotationYaw = before.yaw + MathHelper.wrapAngleTo180_float(after.yaw - before.yaw) * t;
            self.rotationPitch = before.pitch + (after.pitch - before.pitch) * t;
            self.rotationRoll = before.roll + (after.roll - before.roll) * t;
        } else {
            self.posX = before.x;
            self.posY = before.y;
            self.posZ = before.z;
            self.rotationYaw = before.yaw;
            self.rotationPitch = before.pitch;
            self.rotationRoll = before.roll;
        }
        self.rotationYaw = self.rotationYaw % 360.0F;
        self.rotationPitch = self.rotationPitch % 360.0F;
        self.setPosition(self.posX, self.posY, self.posZ);
        ci.cancel();
    }
}
