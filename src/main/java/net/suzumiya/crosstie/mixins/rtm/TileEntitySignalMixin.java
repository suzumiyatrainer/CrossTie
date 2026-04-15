package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.tileentity.TileEntity;
import net.suzumiya.crosstie.CrossTie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * 信号系 TileEntity の最大描画距離を調整する。
 */
@Mixin(targets = "jp.ngt.rtm.electric.TileEntitySignal", remap = false)
public abstract class TileEntitySignalMixin extends TileEntity {

    /**
     * @author CrossTie
     * @reason パフォーマンスのため描画距離を制限する
     */
    @Overwrite
    public double getMaxRenderDistanceSquared() {
        // クライアント側の描画距離設定を基準にする
        // 既定値の 4096.0 (64 ブロック) だと RTM の信号が遠くまで見えないため、少し広めに取る
        if (this.worldObj == null)
            return 4096.0;

        // Proxy 経由で取得したチャンク数から距離を計算する
        if (this.worldObj.isRemote) {
            int renderChunks = CrossTie.proxy.getClientRenderDistance();
            if (renderChunks < 4)
                renderChunks = 4; // 最低保証

            double dist = renderChunks * 16.0;
            // 描画距離の二乗を返す
            return dist * dist;
        }

        return 4096.0;
    }
}
