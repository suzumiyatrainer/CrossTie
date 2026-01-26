package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.electric.TileEntityElectricalWiring;
import net.suzumiya.crosstie.CrossTie;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 架線・コネクタ等の更新処理最適化
 * クライアント側での通電エフェクト（パーティクル）などを距離制限
 */
@Mixin(value = TileEntityElectricalWiring.class, remap = false)
public abstract class TileEntityElectricalWiringMixin extends TileEntity {

    @Inject(method = "updateEntity", at = @At("HEAD"), cancellable = true)
    private void crosstie$cullDistantUpdates(CallbackInfo ci) {
        if (this.worldObj.isRemote) {
            int renderChunks = CrossTie.proxy.getClientRenderDistance();
            if (renderChunks <= 0)
                return;

            // RenderDistance + 2 チャンク
            double cullLimit = (renderChunks + 2) * 16.0;
            double limitSq = cullLimit * cullLimit;

            net.minecraft.entity.Entity player = CrossTie.proxy.getClientPlayer();
            if (player != null
                    && player.getDistanceSq(this.xCoord + 0.5, this.yCoord + 0.5, this.zCoord + 0.5) > limitSq) {
                ci.cancel();
            }
        }
    }
}
