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

    /** 256m 距離カリング閾値（二乗距離） */
    @Unique
    private static final double MAX_RENDER_DIST_SQ = 256.0D * 256.0D;

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

        // === 距離カリング（256m） ===
        double dx = tileEntity.xCoord + 0.5D - renderView.posX;
        double dy = tileEntity.yCoord + 0.5D - renderView.posY;
        double dz = tileEntity.zCoord + 0.5D - renderView.posZ;
        if (dx * dx + dy * dy + dz * dz > MAX_RENDER_DIST_SQ) {
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