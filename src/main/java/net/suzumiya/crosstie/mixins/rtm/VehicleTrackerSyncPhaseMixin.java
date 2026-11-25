package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.train.EntityBogie;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.vehicle.VehicleTrackerEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityTrackerEntry;
import net.suzumiya.crosstie.CrossTieConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * [実験的] 連結車両走行時のめり込み防止（サーバー側）：
 * {@link VehicleTrackerEntry#sendLocationToAllClients(List)} の送信カウンタ ({@code ticks}) を
 * ワールド共通の経過 Tick ({@code worldObj.getTotalWorldTime()}) に同期する。
 *
 * <p>
 * 標準実装では {@code VehicleTrackerEntry.ticks} が各車両エンティティごとに独立してカウントされるため、
 * スポーン／チャンクロード順の違いによって同一編成の前後車両が別々のサーバー Tick で
 * {@code PacketVehicleMovement} を送信し、高速走行時にクライアント側で車間ズレ・めり込みが発生する。
 * </p>
 *
 * <p>
 * {@link CrossTieConfig#fixConnectedTrainClipping} が {@code true} の場合のみ動作する。
 * </p>
 */
@Mixin(value = VehicleTrackerEntry.class, remap = false)
public abstract class VehicleTrackerSyncPhaseMixin {

    @Inject(method = { "sendLocationToAllClients", "func_73122_a" }, at = @At("HEAD"), require = 0, remap = false)
    private void crosstie$syncTrainBroadcastPhase(List<?> players, CallbackInfo ci) {
        if (!CrossTieConfig.fixConnectedTrainClipping) {
            return;
        }
        EntityTrackerEntry self = (EntityTrackerEntry) (Object) this;
        Entity entity = self.myEntity;
        if ((entity instanceof EntityTrainBase || entity instanceof EntityBogie) && entity.worldObj != null) {
            self.ticks = (int) (entity.worldObj.getTotalWorldTime() & 0x7FFFFFFFL);
        }
    }
}
