package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.suzumiya.crosstie.CrossTie;
import net.suzumiya.crosstie.config.CrossTieConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "jp.ngt.rtm.rail.TileEntityLargeRailCore", remap = false)
public abstract class TileEntityLargeRailCoreMixin extends TileEntity {

    @Override
    @SideOnly(Side.CLIENT)
    public double getMaxRenderDistanceSquared() {
        // レールの描画距離をプレイヤーの描画設定に合わせる
        int renderDistance = CrossTie.proxy.getClientRenderDistance();
        if (renderDistance > 0) {
            double blockDistance = renderDistance * 16.0D;
            return blockDistance * blockDistance;
        }
        return super.getMaxRenderDistanceSquared();
    }

    /**
     * Angelica fix: Override getRenderBoundingBox早期に
     * 
     * AngelicaのMixinTileEntityは、getRenderBoundingBoxの結果をキャッシュします。
     * このため、@Overrideメソッドでは遅すぎます（既にキャッシュされた後）。
     * 代わりに@Injectを使用してメソッドの先頭で介入し、結果を変更します。
     */
    @Inject(method = "getRenderBoundingBox", at = @At("HEAD"), cancellable = true, remap = false)
    @SideOnly(Side.CLIENT)
    private void crosstie$fixAngelicaRailCulling(CallbackInfoReturnable<AxisAlignedBB> cir) {
        if (CrossTieConfig.fixAngelicaRailCulling) {
            // Debug log (減らす)
            if (this.xCoord % 10 == 0 && this.zCoord % 10 == 0) {
                FMLLog.info("[CrossTie] Angelica rail fix: Forcing INFINITE_EXTENT_AABB at x=%d, z=%d",
                        this.xCoord, this.zCoord);
            }
            cir.setReturnValue(INFINITE_EXTENT_AABB);
        }
    }
}
