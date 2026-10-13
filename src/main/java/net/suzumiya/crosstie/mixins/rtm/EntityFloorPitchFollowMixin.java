package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.train.parts.EntityFloor;
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import net.minecraft.util.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = { "jp.ngt.rtm.entity.train.parts.EntityFloor" }, remap = false)
public abstract class EntityFloorPitchFollowMixin {

    @SuppressWarnings("rawtypes")
    @Inject(method = "updateRiderPosition", at = @At("TAIL"), remap = false)
    private void crosstie$updateFloorRiderPitch(CallbackInfo ci) {
        EntityFloor floor = (EntityFloor) (Object) this;
        if (floor.riddenByEntity != null) {
            EntityVehicleBase vehicle = floor.getVehicle();
            if (vehicle != null) {
                float dPitch = MathHelper.wrapAngleTo180_float(vehicle.rotationPitch - vehicle.prevRotationPitch);
                if (dPitch != 0.0F) {
                    floor.riddenByEntity.rotationPitch -= dPitch;
                }
            }
        }
    }
}
