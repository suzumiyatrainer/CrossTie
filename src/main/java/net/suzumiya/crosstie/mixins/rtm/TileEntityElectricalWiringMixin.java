package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.tileentity.TileEntity;
import net.suzumiya.crosstie.CrossTie;
import net.suzumiya.crosstie.config.CrossTieConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 電線系 TileEntity の更新を描画距離で抑制する。
 */
@Mixin(targets = "jp.ngt.rtm.electric.TileEntityElectricalWiring", remap = false)
public abstract class TileEntityElectricalWiringMixin extends TileEntity {

    @Inject(method = "updateEntity", at = @At("HEAD"), cancellable = true)
    private void crosstie$cullDistantUpdates(CallbackInfo ci) {
        if (!CrossTieConfig.enableTileEntityUpdates || this.worldObj == null || !this.worldObj.isRemote) {
            return;
        }

        {
            int renderChunks = CrossTie.proxy.getClientRenderDistance();
            if (renderChunks <= 0)
                return;

            // 描画距離 + 2 チャンク
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
