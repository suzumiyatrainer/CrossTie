package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FPS最適化Mixin
 * 
 * 描画負荷の高い車両エンティティのレンダリングを最適化します。
 */
@Mixin(targets = "jp.ngt.rtm.entity.vehicle.RenderVehicleBase", remap = false)
public abstract class RenderVehicleBaseMixin {

    /**
     * 遠距離カリング (Distance Culling)
     * 
     * クライアントの描画距離設定に基づいて、遠くの車両の描画をスキップします。
     * 基準: (描画距離チャンク + 2) * 16 ブロック
     */
    @Inject(method = "doRender", at = @At("HEAD"), cancellable = true)
    private void crosstie$cullDistantVehicles(Entity entity, double x, double y, double z, float yaw,
            float partialTicks, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderViewEntity == null)
            return;

        // 描画距離 (チャンク単位)
        int renderDistChunks = mc.gameSettings.renderDistanceChunks;

        // カリング距離 (ブロック単位)
        // ユーザーの要望: 描画距離 + 2チャンク
        double cullDist = (renderDistChunks + 2) * 16.0;

        // 距離の二乗と比較 (平方根計算を避けるため)
        double distSq = entity.getDistanceSqToEntity(mc.renderViewEntity);

        if (distSq > cullDist * cullDist) {
            ci.cancel();
        }
    }

    /**
     * ライトエフェクト（ボリュームライト）の距離制限
     * 描画距離 - 4チャンク（最低4チャンク）以上離れたら描画しない
     */
    @Inject(method = "renderLightEffect", at = @At("HEAD"), cancellable = true)
    private void crosstie$cullLightEffects(jp.ngt.rtm.entity.vehicle.EntityVehicleBase vehicle,
            jp.ngt.rtm.modelpack.modelset.ModelSetVehicleBaseClient modelset, CallbackInfo ci) {

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderViewEntity == null)
            return;

        int renderChunks = mc.gameSettings.renderDistanceChunks;
        // 最低4チャンク確保、描画距離-4チャンクまで表示
        int effectChunks = Math.max(4, renderChunks - 4);
        double effectDist = effectChunks * 16.0;

        if (vehicle.getDistanceSqToEntity(mc.renderViewEntity) > effectDist * effectDist) {
            ci.cancel();
        }
    }

    /**
     * 方向幕の距離制限
     * 描画距離 + 1チャンク以上離れたら描画しない
     */
    @Inject(method = "renderRollsign", at = @At("HEAD"), cancellable = true)
    private void crosstie$cullRollsigns(jp.ngt.rtm.entity.vehicle.EntityVehicleBase vehicle,
            jp.ngt.rtm.modelpack.modelset.ModelSetVehicleBaseClient modelset, CallbackInfo ci) {

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderViewEntity == null)
            return;

        int renderChunks = mc.gameSettings.renderDistanceChunks;
        // 描画距離 + 1チャンクまで表示
        double signDist = (renderChunks + 1) * 16.0;

        if (vehicle.getDistanceSqToEntity(mc.renderViewEntity) > signDist * signDist) {
            ci.cancel();
        }
    }
}
