package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import net.suzumiya.crosstie.CrossTie;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * レールの描画距離最適化
 * デフォルトの「チャンク読み込み距離」から「描画設定距離」に変更
 */
@Mixin(value = TileEntityLargeRailCore.class, remap = false)
public abstract class TileEntityLargeRailCoreMixin extends TileEntity {

    /**
     * @author CrossTie
     * @reason Limit rail render distance to client settings
     */
    @Overwrite
    public double getMaxRenderDistanceSquared() {
        // サーバー側はデフォルト動作
        if (this.worldObj == null || !this.worldObj.isRemote) {
            return 65536.0D; // 256*256 (fallback) or larger
        }

        // クライアントの描画距離設定を取得
        int renderChunks = CrossTie.proxy.getClientRenderDistance();
        if (renderChunks < 4)
            renderChunks = 4; // 最低保証

        double dist = renderChunks * 16.0;

        // レールは長いので、少し余裕を持たせる（+1チャンク分）
        dist += 16.0;

        return dist * dist;
    }
}
