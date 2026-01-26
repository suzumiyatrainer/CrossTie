package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.electric.TileEntitySignal;
import net.suzumiya.crosstie.CrossTie;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * 信号機の描画距離最適化
 * RTMデフォルトの「チャンク読み込み距離全体」から、
 * バニラ標準または設定された描画距離に基づいて制限する。
 */
@Mixin(value = TileEntitySignal.class, remap = false)
public abstract class TileEntitySignalMixin extends TileEntity {

    /**
     * @author CrossTie
     * @reason Limit render distance for performance
     */
    @Overwrite
    public double getMaxRenderDistanceSquared() {
        // クライアントの描画距離設定を取得
        // デフォルトでは 4096.0 (64ブロック) だが、RTMモデルは大きいので少し広めに確保
        // 例えば 描画距離 * 16 の 2乗

        if (this.worldObj == null)
            return 4096.0;

        // Proxy経由で取得したチャンク数から距離を計算
        // ただしサーバー側で呼ばれる可能性もあるため、安全策をとる
        if (this.worldObj.isRemote) {
            int renderChunks = CrossTie.proxy.getClientRenderDistance();
            if (renderChunks < 4)
                renderChunks = 4; // 最低保証

            double dist = renderChunks * 16.0;
            // 少し余裕を持たせる (例: 128ブロック先まで見えれば十分？)
            // RTMユーザーは遠くの信号を見たいかもしれないので、描画距離そのままを使う
            return dist * dist;
        }

        return 4096.0;
    }
}
