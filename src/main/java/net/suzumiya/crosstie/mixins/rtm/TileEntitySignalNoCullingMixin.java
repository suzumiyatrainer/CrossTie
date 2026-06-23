package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.suzumiya.crosstie.CrossTieConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("all")
@Mixin(targets = "jp.ngt.rtm.electric.TileEntitySignal", remap = false)
public abstract class TileEntitySignalNoCullingMixin {

    /**
     * クライアント描画距離の2倍（チャンク数×32）を上限とする。
     * 設定変更時の再計算を避けるためキャッシュ。
     */
    @Unique
    private static int crosstie$lastRenderDist = -1;
    @Unique
    private static double crosstie$cachedMaxDistSq = Double.MAX_VALUE;

    @Unique
    private static double crosstie$getMaxRenderDistanceSq() {
        int currentDist = (Minecraft.getMinecraft().gameSettings.renderDistanceChunks + 1) * 16;
        if (currentDist != crosstie$lastRenderDist) {
            crosstie$lastRenderDist = currentDist;
            crosstie$cachedMaxDistSq = (double) currentDist * (double) currentDist;
        }
        return crosstie$cachedMaxDistSq;
    }

    /**
     * @author CrossTie
     * @reason Prevent frustum culling: return INFINITE_EXTENT_AABB so that
     *         Angelica's TileEntityRenderBoundsRegistry classifies this as INFINITE
     *         and always renders it regardless of view direction.
     */
    @Inject(method = "getRenderBoundingBox", at = @At("HEAD"), cancellable = true, remap = false)
    @SideOnly(Side.CLIENT)
    public void injectGetRenderBoundingBox(CallbackInfoReturnable<AxisAlignedBB> cir) {
        if (!CrossTieConfig.disableSignalCulling) {
            return;
        }
        cir.setReturnValue(TileEntity.INFINITE_EXTENT_AABB);
    }

    /**
     * @author CrossTie
     * @reason Limit max render distance to (renderDistanceChunks * 32) blocks
     *         to avoid rendering signals at extreme distances in non-Angelica environments.
     */
    @Inject(method = "getMaxRenderDistanceSquared", at = @At("HEAD"), cancellable = true, remap = false)
    @SideOnly(Side.CLIENT)
    public void injectMaxRenderDistanceSquared(CallbackInfoReturnable<Double> cir) {
        if (!CrossTieConfig.disableSignalCulling) {
            return;
        }
        cir.setReturnValue(crosstie$getMaxRenderDistanceSq());
    }

}