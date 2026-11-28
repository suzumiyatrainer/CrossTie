package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.train.parts.EntityVehiclePart;
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import net.suzumiya.crosstie.cache.VehicleUUIDIndex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * EntityVehiclePart.loadTrainFromUUID() の O(N) loadedEntityList 線形スキャンを
 * {@link VehicleUUIDIndex} による真の O(1) ルックアップに最適化する。
 */
@SuppressWarnings("rawtypes")
@Mixin(value = EntityVehiclePart.class, remap = false)
public abstract class EntityVehiclePartUUIDLookupMixin {

    @Inject(method = "loadTrainFromUUID", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$lookupVehicleByUUID(UUID uuid, CallbackInfoReturnable<Boolean> cir) {
        EntityVehiclePart self = (EntityVehiclePart) (Object) this;
        if (self.worldObj == null || uuid == null) {
            return;
        }

        EntityVehicleBase<?> vehicle = VehicleUUIDIndex.get(self.worldObj, uuid);
        if (vehicle != null) {
            self.setVehicle(vehicle);
            self.onLoadVehicle();
            cir.setReturnValue(true);
        }
        // インデックス未ヒット時のみオリジナルの線形スキャンへフォールバック
    }
}
