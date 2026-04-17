package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import jp.ngt.rtm.modelpack.modelset.ModelSetVehicleBaseClient;
import net.suzumiya.crosstie.config.CrossTieConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RTM の車両描画を距離で間引く mixin。
 */
@Mixin(targets = "jp.ngt.rtm.entity.vehicle.RenderVehicleBase", remap = false)
public abstract class RenderVehicleBaseMixin {

    /**
     * 車両本体の描画を、プレイヤーから遠い場合は止める。
     */
    @Inject(method = "doRender", at = @At("HEAD"), cancellable = true)
    private void crosstie$cullDistantVehicles(Entity entity, double x, double y, double z, float yaw,
            float partialTicks, CallbackInfo ci) {
        if (!CrossTieConfig.enableRenderCulling) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderViewEntity == null)
            return;

        // 描画距離（チャンク単位）
        int renderDistChunks = mc.gameSettings.renderDistanceChunks;

        // カリング距離（ブロック単位）
        // 描画距離 + 2 チャンクを基準にする
        double cullDist = (renderDistChunks + 2) * 16.0;

        // 距離の二乗で比較する（平方根を避けるため）
        double distSq = entity.getDistanceSqToEntity(mc.renderViewEntity);

        if (distSq > cullDist * cullDist) {
            ci.cancel();
        }
    }

    /**
     * ライトエフェクトの描画を、描画距離の外では止める。
     */
    @Inject(method = "renderLightEffect", at = @At("HEAD"), cancellable = true)
    private void crosstie$cullLightEffects(EntityVehicleBase vehicle, ModelSetVehicleBaseClient modelset,
            CallbackInfo ci) {
        if (!CrossTieConfig.enableRenderCulling) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderViewEntity == null)
            return;

        int renderChunks = mc.gameSettings.renderDistanceChunks;
        // 最低 4 チャンクを確保し、描画距離 - 4 チャンクまで表示する
        int effectChunks = Math.max(4, renderChunks - 4);
        double effectDist = effectChunks * 16.0;

        if (vehicle.getDistanceSqToEntity(mc.renderViewEntity) > effectDist * effectDist) {
            ci.cancel();
        }
    }

    /**
     * ロールサインの描画を、描画距離の外では止める。
     */
    @Inject(method = "renderRollsign", at = @At("HEAD"), cancellable = true)
    private void crosstie$cullRollsigns(EntityVehicleBase vehicle, ModelSetVehicleBaseClient modelset,
            CallbackInfo ci) {
        if (!CrossTieConfig.enableRenderCulling) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderViewEntity == null)
            return;

        int renderChunks = mc.gameSettings.renderDistanceChunks;
        // 描画距離 + 1 チャンクまで表示する
        double signDist = (renderChunks + 1) * 16.0;

        if (vehicle.getDistanceSqToEntity(mc.renderViewEntity) > signDist * signDist) {
            ci.cancel();
        }
    }
}
