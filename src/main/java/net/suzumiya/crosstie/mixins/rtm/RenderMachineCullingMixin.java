package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.block.tileentity.TileEntityCrossingGate;
import jp.ngt.rtm.block.tileentity.TileEntityMachineBase;
import jp.ngt.rtm.electric.TileEntitySignal;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.client.MinecraftForgeClient;
import net.suzumiya.crosstie.CrossTieConfig;
import net.suzumiya.crosstie.client.DistantMachineSimpleRenderer;
import net.suzumiya.crosstie.utils.ActionPartsMathPicker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RTM {@code RenderMachine} の距離ベース描画カリング・シャドウパス分離・16 チャンク（256m）以遠の超軽量簡略描画。
 *
 * <ul>
 *   <li>シャドウパス: 96m 超で影描画をスキップ。</li>
 *   <li>16 チャンク（256m）超のメインパス: TileEntity 内の変数は一切維持したまま、
 *       pass 1（半透明/発光）をスキップし、pass 0 は JS スクリプト・アニメーション・パーツ分割をバイパスした
 *       共有 1-VBO 静的 DisplayList ({@link DistantMachineSimpleRenderer}) で描画する。</li>
 *   <li>最大描画距離超: 描画をスキップ。</li>
 * </ul>
 */
@Mixin(targets = "jp.ngt.rtm.block.tileentity.RenderMachine", remap = false)
public abstract class RenderMachineCullingMixin {

    @Unique
    private static final double SHADOW_MAX_DIST_SQ = 96.0D * 96.0D;

    /** 16 チャンク (256m) 超で簡略描画に切り替える閾値 */
    @Unique
    private static final double SIMPLIFIED_LOD_DIST_SQ = 256.0D * 256.0D;

    @Unique
    private static int crosstie$lastRenderChunks = -1;
    @Unique
    private static double crosstie$cachedMaxDistSq = Double.MAX_VALUE;

    @Unique
    private static void crosstie$updateDistances(int renderChunks) {
        if (renderChunks != crosstie$lastRenderChunks) {
            crosstie$lastRenderChunks = renderChunks;
            double maxDist = (double) renderChunks * 16.0D;
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

        // シャドウパス時は遠方の小型機械の影描画を早めにカリング
        if (ActionPartsMathPicker.isShadowPassActive()) {
            double shadowLimitSq = Math.min(crosstie$cachedMaxDistSq, SHADOW_MAX_DIST_SQ);
            if (distSq > shadowLimitSq) {
                ci.cancel();
            }
            return;
        }

        // disableSignalCulling 有効時は信号機・踏切のメインパス描画を保護
        if (CrossTieConfig.disableSignalCulling
                && (tileEntity instanceof TileEntitySignal || tileEntity instanceof TileEntityCrossingGate)) {
            return;
        }

        // 最大描画距離（クライアント描画距離）以降は描画のみスキップ
        if (distSq > crosstie$cachedMaxDistSq) {
            ci.cancel();
            return;
        }

        // Phase 5.5: 16 チャンク (256m) より遠い RTM 機械類は内部変数を維持したまま描画のみ圧倒的簡略化
        if (distSq > SIMPLIFIED_LOD_DIST_SQ && tileEntity instanceof TileEntityMachineBase) {
            int pass = MinecraftForgeClient.getRenderPass();
            if (pass == 1) {
                // 遠距離の半透明・発光 2 パス目はスキップ
                ci.cancel();
                return;
            }
            if (DistantMachineSimpleRenderer.renderSimplifiedPass0((TileEntityMachineBase) tileEntity, x, y, z)) {
                ci.cancel();
            }
        }
    }
}
