package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import net.suzumiya.crosstie.utils.VehicleSeatSyncHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@SuppressWarnings("rawtypes")
@Mixin(value = EntityVehicleBase.class, remap = false)
public abstract class EntityVehicleBaseSeatSyncMixin {

    @Shadow
    protected List vehicleFloors;

    /**
     * クライアント側で電車の updatePosAndRotationClient() が完了した直後、
     * 車内の全座席（EntityFloor）および座席・運転席に乗っているライダーを即座に最新位置・姿勢へ同期する。
     * これにより、loadedEntityListの更新順序に関わらず座席とプレイヤーが電車に100%追従し、 前後ジッター（カクつき）を根絶する。
     */
    @Inject(method = { "updatePosAndRotationClient()V",
            "updatePosAndRotationClient" }, at = @At("TAIL"), require = 0, remap = false)
    private void crosstie$syncFloorsOnClientUpdate(CallbackInfo ci) {
        EntityVehicleBase self = (EntityVehicleBase) (Object) this;
        VehicleSeatSyncHandler.syncVehicleFloorsAndRiders(self, this.vehicleFloors);
    }

    /**
     * サーバー側で電車の onVehicleUpdate()（物理移動・スクリプト実行等）が完了した直後、
     * 車内の全座席および搭乗ライダーの位置をサーバー側でも即座に同期する。
     */
    @Inject(method = { "onVehicleUpdate()V", "onVehicleUpdate" }, at = @At("TAIL"), require = 0, remap = false)
    private void crosstie$syncFloorsOnServerUpdate(CallbackInfo ci) {
        EntityVehicleBase self = (EntityVehicleBase) (Object) this;
        if (!self.worldObj.isRemote) {
            VehicleSeatSyncHandler.syncVehicleFloorsAndRiders(self, this.vehicleFloors);
        }
    }
}
