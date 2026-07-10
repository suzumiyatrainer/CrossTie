package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.suzumiya.crosstie.CrossTieConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * LargeRail (大型レール) の描画最適化 — 統合版。
 *
 * <p>
 * 旧 {@code RTMRailTESRThrottleMixin} のスロットリングロジックをこのクラスへ統合し、
 * {@code renderTileEntityAt} に対するフックを1本化した。
 *
 * <h3>適用される最適化</h3>
 * <ol>
 * <li><b>距離カリング</b>: クライアントの描画距離（chunks × 16）を上限に、 最低256mまでのレールのみ描画する。</li>
 * <li><b>距離別スロットリング</b>: 中〜遠距離のレール描画頻度を間引く。 {@code railTesrThrottleEnabled}
 * が無効の場合はスキップ。
 * <ul>
 * <li>64m以内: 毎フレーム</li>
 * <li>160m以内: 5フレームに1回</li>
 * <li>それ以上: 10フレームに1回</li>
 * </ul>
 * </li>
 * <li><b>フラストラムカリング</b>: 視界外のレールをスキップする。 {@code largeRailCullingEnabled}
 * が無効の場合はスキップ。</li>
 * </ol>
 */
@Mixin(targets = "jp.ngt.rtm.rail.RenderLargeRail", remap = false)
public abstract class RenderLargeRailOptimizationMixin {

    // ========================
    // 距離カリング用キャッシュ
    // ========================

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

    // ========================
    // スロットリング用定数・カウンター
    // ========================

    @Unique
    private static final int THROTTLE_NEAR = 1;
    @Unique
    private static final int THROTTLE_MID = 5;
    @Unique
    private static final int THROTTLE_FAR = 10;
    @Unique
    private static final double NEAR_DIST_SQ = 64.0 * 64.0;
    @Unique
    private static final double MID_DIST_SQ = 160.0 * 160.0;
    @Unique
    private static int crosstie$frameCounter = 0;

    // ========================
    // フラストラムキャッシュ
    // ========================

    @Unique
    private static ICamera crosstie$frustrum = null;

    /**
     * LargeRailCore の {@code renderTileEntityAt} をフックし、 距離カリング → スロットリング →
     * フラストラムカリングの順で不要な描画をスキップする。
     */
    @Inject(method = "renderTileEntityAt", at = @At("HEAD"), cancellable = true, remap = true)
    private void crosstie$cullAndThrottle(TileEntity tileEntity, double d0, double d1, double d2, float f,
            CallbackInfo ci) {

        if (tileEntity == null || !"jp.ngt.rtm.rail.TileEntityLargeRailCore".equals(tileEntity.getClass().getName())) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        Entity renderView = mc.renderViewEntity;
        if (renderView == null) {
            return;
        }

        double dx = tileEntity.xCoord + 0.5D - renderView.posX;
        double dy = tileEntity.yCoord + 0.5D - renderView.posY;
        double dz = tileEntity.zCoord + 0.5D - renderView.posZ;
        double distSq = dx * dx + dy * dy + dz * dz;

        // === 1. 距離カリング（描画距離上限） ===
        if (distSq > crosstie$getMaxRenderDistanceSq()) {
            ci.cancel();
            return;
        }

        // === 2. 距離別スロットリング ===
        if (CrossTieConfig.railTesrThrottleEnabled) {
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
        }

        // === 3. フラストラムカリング ===
        // 注: camera.setPosition() は glGetFloat を呼び出し毎フレーム・毎レールでGPUパイプラインを
        // ストールさせるため、極度のFPS低下（シェーダー時など）を引き起こすため削除。
        // バニラのチャンクカリングと上記の距離カリング・スロットリングのみに依存する。
    }
}