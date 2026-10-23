package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.train.parts.EntityFloor;
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import net.suzumiya.crosstie.utils.VehicleSeatSyncHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityFloor.class, remap = false)
public abstract class EntityFloorSyncMixin {

    /**
     * 座席自身の onUpdate() の末尾でも、親車両が存在する場合は VehicleSeatSyncHandler を通じて prevPos と pos
     * の厳密同期を維持する。
     */
    @SuppressWarnings("rawtypes")
    @Inject(method = { "onUpdate()V", "onUpdate", "func_70071_h_" }, at = @At("TAIL"), require = 0, remap = false)
    private void crosstie$maintainFloorSync(CallbackInfo ci) {
        EntityFloor self = (EntityFloor) (Object) this;
        EntityVehicleBase vehicle = self.getVehicle();
        if (vehicle != null && !vehicle.isDead) {
            VehicleSeatSyncHandler.syncFloorAndRider(self, vehicle);
        }
    }
}
