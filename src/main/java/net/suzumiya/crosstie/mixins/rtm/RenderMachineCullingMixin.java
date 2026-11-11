package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.suzumiya.crosstie.CrossTieConfig;
import net.suzumiya.crosstie.client.CrossTieRenderFrameTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RTM {@code RenderMachine} の距離ベース描画LODおよび遠距離描画カリング。
 *
 * <p>
 * ■ 動作仕様:
 * <ul>
 * <li><b>総チャンクの半分まで (0 ～ D_half):</b> 常に通常描画（毎フレーム描画）。</li>
 * <li><b>総チャンクの半分以遠 ～ 最大描画距離 (D_half ～ D_max):</b> 描画頻度を半減（2フレームに1回描画）。
 *     座標ハッシュにより描画タイミングをフレーム間で分散し、描画負荷を平滑化します。
 *     同一フレーム内の Pass 0 / Pass 1 で判定が一致するため、半透明パーツの欠落や点滅は発生しません。</li>
 * <li><b>最大描画距離超過 (D_max 以遠):</b> 描画のみ（{@code renderTileEntityAt}）をスキップ。
 *     TileEntity の内部ロジック・変数処理（{@code updateEntity}、アニメーションタイマー、NBT同期等）には一切触れません。</li>
 * </ul>
 *
 * <p>
 * 視界外カリング（Frustum Culling）を行わず視点からの距離判定を行うため、
 * 踏切の遮断桿やポイント、信号機などの特殊形状・大型パーツが画角端で突然消える問題が発生しません。
 */
@Mixin(targets = "jp.ngt.rtm.block.tileentity.RenderMachine", remap = false)
public abstract class RenderMachineCullingMixin {

    @Unique
    private static int crosstie$lastRenderChunks = -1;
    @Unique
    private static double crosstie$cachedHalfDistSq = 0.0D;
    @Unique
    private static double crosstie$cachedMaxDistSq = Double.MAX_VALUE;

    @Unique
    private static void crosstie$updateDistances(int renderChunks) {
        if (renderChunks != crosstie$lastRenderChunks) {
            crosstie$lastRenderChunks = renderChunks;
            // 総チャンクの半分（ブロック換算: renderChunks * 8.0）
            double halfDist = (double) renderChunks * 8.0D;
            // 最大描画距離（ブロック換算: renderChunks * 16.0）
            double maxDist = (double) renderChunks * 16.0D;
            crosstie$cachedHalfDistSq = halfDist * halfDist;
            crosstie$cachedMaxDistSq = maxDist * maxDist;
        }
    }

    @Inject(method = "renderTileEntityAt", at = @At("HEAD"), cancellable = true, remap = true)
    private void crosstie$controlMachineRendering(TileEntity tileEntity, double x, double y, double z, float partialTicks,
            CallbackInfo ci) {
        if (!CrossTieConfig.machineTesrCullingEnabled || tileEntity == null) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        Entity viewer = mc.renderViewEntity;
        if (viewer == null) {
            return;
        }

        crosstie$updateDistances(mc.gameSettings.renderDistanceChunks);

        double dx = tileEntity.xCoord + 0.5D - viewer.posX;
        double dy = tileEntity.yCoord + 0.5D - viewer.posY;
        double dz = tileEntity.zCoord + 0.5D - viewer.posZ;
        double distSq = dx * dx + dy * dy + dz * dz;

        // 1. 総チャンクの半分までは通常描画
        if (distSq <= crosstie$cachedHalfDistSq) {
            return;
        }

        // 2. 最大描画距離（クライアント描画距離）以降は描画のみスキップ
        if (distSq > crosstie$cachedMaxDistSq) {
            ci.cancel();
            return;
        }

        // 3. 半分以遠 ～ 最大距離までは描画頻度を半減（2フレームに1回描画）
        int frame = CrossTieRenderFrameTracker.getRenderFrame();
        int hash = tileEntity.xCoord ^ tileEntity.yCoord ^ tileEntity.zCoord;
        if (((hash + frame) & 1) != 0) {
            ci.cancel();
        }
    }
}
