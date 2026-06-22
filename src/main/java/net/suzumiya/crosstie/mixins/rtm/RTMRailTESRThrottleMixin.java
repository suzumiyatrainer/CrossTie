package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.rail.RenderLargeRail;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
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

/**
 * Reduces TESR update frequency for distant RTM rails to improve FPS.
 * <p>
 * Nearby rails render every frame, mid-range every 5 frames, far rails every 10 frames.
 * Also adds frustum culling to skip rails outside the view.
 */
@Mixin(targets = "jp.ngt.rtm.rail.RenderLargeRail", remap = false)
public abstract class RTMRailTESRThrottleMixin {

    @Unique
    private static final int THROTTLE_NEAR = 1;    // every frame
    @Unique
    private static final int THROTTLE_MID = 5;     // every 5 frames
    @Unique
    private static final int THROTTLE_FAR = 10;    // every 10 frames
    @Unique
    private static final double NEAR_DIST_SQ = 64.0 * 64.0;    // 64 blocks
    @Unique
    private static final double MID_DIST_SQ = 160.0 * 160.0;   // 160 blocks (5 chunks)

    @Unique
    private static int crosstie$frameCounter = 0;

    @Unique
    private static ICamera crosstie$frustrum = null;

    @Inject(method = "renderTileEntityAt", at = @At("HEAD"), cancellable = true, remap = true)
    private void crosstie$throttleAndCull(TileEntity tileEntity, double d0, double d1, double d2, float f, CallbackInfo ci) {
        if (!(tileEntity instanceof TileEntityLargeRailCore)) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        Entity renderView = mc.renderViewEntity;
        if (renderView == null) {
            return;
        }

        // === Distance-based throttling ===
        double dx = tileEntity.xCoord + 0.5D - renderView.posX;
        double dy = tileEntity.yCoord + 0.5D - renderView.posY;
        double dz = tileEntity.zCoord + 0.5D - renderView.posZ;
        double distSq = dx * dx + dy * dy + dz * dz;

        int throttle;
        if (distSq <= NEAR_DIST_SQ) {
            throttle = THROTTLE_NEAR;
        } else if (distSq <= MID_DIST_SQ) {
            throttle = THROTTLE_MID;
        } else {
            throttle = THROTTLE_FAR;
        }

        if (throttle > 1) {
            crosstie$frameCounter++;
            if ((crosstie$frameCounter % throttle) != 0) {
                ci.cancel();
                return;
            }
        }

        // === Frustum culling ===
        ICamera camera = crosstie$frustrum;
        if (camera == null) {
            camera = new Frustrum();
            crosstie$frustrum = camera;
        }
        camera.setPosition(renderView.posX, renderView.posY, renderView.posZ);

        if (tileEntity.hasWorldObj()) {
            AxisAlignedBB aabb = tileEntity.getRenderBoundingBox();
            if (aabb != null) {
                if (!camera.isBoundingBoxInFrustum(
                        AxisAlignedBB.getBoundingBox(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ))) {
                    ci.cancel();
                }
            }
        }
    }
}