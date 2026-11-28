package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.train.EntityBogie;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import net.suzumiya.crosstie.cache.VehicleUUIDIndex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * EntityBogie.loadTrainFromUUID() の O(N) loadedEntityList 線形スキャンを
 * {@link VehicleUUIDIndex} による真の O(1) ルックアップに最適化する。
 */
@Mixin(value = EntityBogie.class, remap = false)
public abstract class EntityBogieUUIDLookupMixin {

    @Inject(method = "loadTrainFromUUID", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$lookupTrainByUUID(UUID uuid, CallbackInfoReturnable<Boolean> cir) {
        EntityBogie self = (EntityBogie) (Object) this;
        if (self.worldObj == null || uuid == null) {
            return;
        }

        EntityVehicleBase<?> vehicle = VehicleUUIDIndex.get(self.worldObj, uuid);
        if (vehicle instanceof EntityTrainBase) {
            EntityTrainBase train = (EntityTrainBase) vehicle;
            self.setTrain(train);
            train.setBogie(self.getBogieId(), self);
            cir.setReturnValue(true);
        }
        // インデックス未ヒット時のみオリジナルの線形スキャンへフォールバック
    }
}
