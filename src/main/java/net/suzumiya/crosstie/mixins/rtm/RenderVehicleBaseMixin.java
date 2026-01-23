package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.suzumiya.crosstie.CrossTie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RenderVehicleBase最適化Mixin
 * 
 * FPS（クライアント描画）最適化:
 * - 遠距離車両の描画スキップ（視錐台カリング強化）
 * - ライトエフェクトの距離制限
 * - LOD（Level of Detail）導入の基盤
 */
@Mixin(targets = "jp.ngt.rtm.entity.vehicle.RenderVehicleBase", remap = false)
public abstract class RenderVehicleBaseMixin {

    @Unique
    private static final double CROSSTIE$MAX_RENDER_DISTANCE = 200.0D;

    @Unique
    private static final double CROSSTIE$LIGHT_EFFECT_DISTANCE = 64.0D;

    /**
     * 遠距離車両の描画をスキップ
     * 
     * プレイヤーから一定距離以上離れた車両の描画をスキップして、
     * FPSを改善します。
     */
    @Inject(method = "renderVehicleBase", at = @At("HEAD"), cancellable = true, require = 0)
    private void crosstie$enhancedDistanceCulling(
            Object vehicle, // EntityVehicleBase (外部クラスなのでObjectで受ける)
            double x,
            double y,
            double z,
            float yaw,
            float partialTicks,
            CallbackInfo ci) {
        // 距離計算
        double distanceSquared = x * x + y * y + z * z;
        double maxDistanceSquared = CROSSTIE$MAX_RENDER_DISTANCE * CROSSTIE$MAX_RENDER_DISTANCE;

        // 最大描画距離を超えていたら描画をキャンセル
        if (distanceSquared > maxDistanceSquared) {
            ci.cancel();
        }
    }

    /**
     * ライトエフェクトの距離制限
     * 
     * プレイヤーから一定距離以上離れた車両のライトエフェクトを無効化して、
     * 描画コストを削減します。
     */
    @Inject(method = "renderLightEffect(Ljp/ngt/rtm/entity/vehicle/EntityVehicleBase;Ljp/ngt/rtm/modelpack/modelset/ModelSetVehicleBaseClient;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void crosstie$limitLightEffectDistance(Object vehicle, Object modelset, CallbackInfo ci) {
        try {
            // リフレクションを使わずに距離を計算
            EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            if (player != null && vehicle instanceof net.minecraft.entity.Entity) {
                net.minecraft.entity.Entity entityVehicle = (net.minecraft.entity.Entity) vehicle;
                double distance = player.getDistanceToEntity(entityVehicle);

                // ライトエフェクト描画距離を超えていたらキャンセル
                if (distance > CROSSTIE$LIGHT_EFFECT_DISTANCE) {
                    ci.cancel();
                }
            }
        } catch (Exception e) {
            // エラー発生時はログに記録して処理を続行
            CrossTie.LOGGER.warn("Error in light effect distance check", e);
        }
    }

    // TODO: バッチレンダリングの導入
    // 同一テクスチャを使用する車両をまとめて描画することで、
    // テクスチャバインドの回数を削減し、FPSを改善
}
