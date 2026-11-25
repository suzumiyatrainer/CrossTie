package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import jp.ngt.rtm.network.PacketVehicleMovement;
import net.suzumiya.crosstie.CrossTieConfig;
import net.suzumiya.crosstie.client.ConnectedTrainSyncHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * [実験的] 連結車両走行時のめり込み防止（クライアント側）：
 * {@link PacketVehicleMovement#onMessage} が Netty IO スレッドから直接エンティティの
 * 補間ターゲット ({@code vehicleX/Y/Z, vehiclePosRotationInc}) を書き換える競合を防ぎ、
 * {@link ConnectedTrainSyncHandler} 経由でクライアントメインスレッドの Tick 開始時に一括適用する。
 *
 * <p>
 * {@link CrossTieConfig#fixConnectedTrainClipping} が {@code true} の場合のみ動作する。
 * </p>
 */
@Mixin(value = PacketVehicleMovement.class, remap = false)
public abstract class PacketVehicleMovementSyncMixin {

    @Inject(
        method = "onMessage(Ljp/ngt/rtm/network/PacketVehicleMovement;Lcpw/mods/fml/common/network/simpleimpl/MessageContext;)Lcpw/mods/fml/common/network/simpleimpl/IMessage;",
        at = @At("HEAD"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private void crosstie$queueMovementOnClientThread(
            PacketVehicleMovement message,
            MessageContext ctx,
            CallbackInfoReturnable<IMessage> cir) {
        if (!CrossTieConfig.fixConnectedTrainClipping) {
            return;
        }
        if (message != null) {
            ConnectedTrainSyncHandler.enqueue(
                    message.entityId,
                    message.trainX,
                    message.trainY,
                    message.trainZ,
                    message.trainYaw,
                    message.trainPitch,
                    message.trainRoll,
                    message.trainSpeed
            );
        }
        cir.setReturnValue(null);
    }
}
