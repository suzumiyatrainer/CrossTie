package net.suzumiya.crosstie.mixins.bamboo;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BambooのRenderCampfire最適化用Mixin
 * - 目的: キャンプファイヤーや和風光源ブロックの描画時における不要な計算の削減。
 */
@Mixin(targets = "ruby.bamboo.render.tileentity.RenderCampfire", remap = false)
public class BambooRenderCampfireMixin {

    @Inject(method = "renderTileEntityAt", at = @At("HEAD"), cancellable = true)
    public void onRenderTileEntityAt(Object tileEntity, double x, double y, double z, float partialTicks, CallbackInfo ci) {
        // TODO: プレイヤーからの距離や視界内（Frustum Culling）の判定を行い、
        // 描画範囲外であれば ci.cancel() を呼び出して処理をスキップする。
    }
}
