package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "jp.ngt.rtm.rail.RenderLargeRail", remap = false)
public abstract class RenderLargeRailOptimizationMixin {

    /**
     * 距離カリング閾値（二乗距離）。
     * 描画距離設定(chunks × 32) を上限とし、最小 256m を下回らないようにする。
     * 設定変更時の再計算を避けるため、前回値からの変動がなければキャッシュを返す。
     */
    @Unique
    private static int crosstie$lastRenderDist = -1;
    @Unique
    private static double crosstie$cachedMaxDistSq = 256.0D * 256.0D;

    @Unique
    private static double crosstie$getMaxRenderDistanceSq() {
        int currentDist = (Minecraft.getMinecraft().gameSettings.renderDistanceChunks + 1) * 16;
        if (currentDist != crosstie$lastRenderDist) {
            crosstie$lastRenderDist = currentDist;
            double distSq = (double) currentDist * (double) currentDist;
            crosstie$cachedMaxDistSq = Math.max(distSq, 256.0D * 256.0D);
        }
        return crosstie$cachedMaxDistSq;
    }

    /** フラストラムキャッシュ（毎フレーム {@link #renderTileEntityAt} 間で共有） */
    @Unique
    private static ICamera crosstie$frustrum = null;

    /**
     * LargeRailCore の {@code renderTileEntityAt} をフックし、
     * 256m距離カリング＋フラストラムカリングで不要な描画をスキップする。
     */
    @Inject(method = "renderTileEntityAt", at = @At("HEAD"), cancellable = true, remap = true)
    private void crosstie$distanceAndFrustumCulling(TileEntity tileEntity,
            double d0, double d1, double d2, float f, CallbackInfo ci) {
        if (tileEntity == null || !"jp.ngt.rtm.rail.TileEntityLargeRailCore"
                .equals(tileEntity.getClass().getName())) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        Entity renderView = mc.renderViewEntity;
        if (renderView == null) {
            return;
        }

        // === 距離カリング（動的：描画距離×32 〜 最低256m） ===
        double dx = tileEntity.xCoord + 0.5D - renderView.posX;
        double dy = tileEntity.yCoord + 0.5D - renderView.posY;
        double dz = tileEntity.zCoord + 0.5D - renderView.posZ;
        if (dx * dx + dy * dy + dz * dz > crosstie$getMaxRenderDistanceSq()) {
            ci.cancel();
            return;
        }

        // === 簡易フラストラムカリング ===
        // Frustrum を遅延初期化（renderView の位置/回転が変わったら再生成）
        ICamera camera = crosstie$frustrum;
        if (camera == null) {
            camera = new Frustrum();
            crosstie$frustrum = camera;
        }
        camera.setPosition(renderView.posX, renderView.posY, renderView.posZ);

        if (tileEntity.hasWorldObj()) {
            AxisAlignedBB aabb = tileEntity.getRenderBoundingBox();
            if (aabb != null) {
                double minX = aabb.minX;
                double minY = aabb.minY;
                double minZ = aabb.minZ;
                double maxX = aabb.maxX;
                double maxY = aabb.maxY;
                double maxZ = aabb.maxZ;
                if (!camera.isBoundingBoxInFrustum(
                        AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ))) {
                    ci.cancel();
                }
            }
        }
    }
}