package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.suzumiya.crosstie.CrossTieConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RTM {@code RenderMachine} の遠距離カリング。
 *
 * <p>
 * 判定フロー:
 * <ol>
 * <li>プレイヤーとの平方距離を計算する。</li>
 * <li>13ブロック以内なら無条件で通常描画する（セーフティネット）。</li>
 * <li>(描画距離 + 1チャンク) を超える場合は {@code renderTileEntityAt} を早期リターンする。</li>
 * <li>範囲内なら通常描画する。</li>
 * </ol>
 *
 * <p>
 * 対象は {@code TileEntityLight} / {@code TileEntitySpeaker} / {@code TileEntityTicketVendor}
 * のみ。{@code TileEntityCrossingGate} / {@code TileEntityTurnstile} /
 * {@code TileEntityPoint} などの機能物は対象外。
 *
 * <p>
 * 描画のみをスキップし、TE 内部状態（{@code updateEntity} / NBT）には一切触れない。
 * JS の有無に関わらず同一に扱う（A案: 範囲外は描画ごとスキップ）。
 */
@Mixin(targets = "jp.ngt.rtm.block.tileentity.RenderMachine", remap = false)
public abstract class RenderMachineCullingMixin {

    /** 描画距離に加える余裕チャンク数。 */
    @Unique
    private static final int CROSSTIE$EXTRA_CHUNKS = 1;

    /** 常に描画を許可する近距離セーフティネット半径（ブロック）。 */
    @Unique
    private static final double CROSSTIE$SAFE_RADIUS_SQ = 13.0D * 13.0D;

    @Unique
    private static int crosstie$lastRenderChunks = -1;
    @Unique
    private static double crosstie$cachedMaxDistSq = Double.MAX_VALUE;

    /** TE クラス → 対象可否のキャッシュ。 */
    @Unique
    private static final ClassValue<Boolean> CROSSTIE$TARGET = new ClassValue<Boolean>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            // サブクラスも含めて判定するため、継承階層の名前を走査する
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                String name = c.getName();
                if ("jp.ngt.rtm.block.tileentity.TileEntityLight".equals(name)
                        || "jp.ngt.rtm.electric.TileEntitySpeaker".equals(name)
                        || "jp.ngt.rtm.electric.TileEntityTicketVendor".equals(name)) {
                    return Boolean.TRUE;
                }
            }
            return Boolean.FALSE;
        }
    };

    @Unique
    private static double crosstie$getMaxDistSq(int renderChunks) {
        if (renderChunks != crosstie$lastRenderChunks) {
            crosstie$lastRenderChunks = renderChunks;
            double dist = (renderChunks + CROSSTIE$EXTRA_CHUNKS) * 16.0D;
            crosstie$cachedMaxDistSq = dist * dist;
        }
        return crosstie$cachedMaxDistSq;
    }

    @Inject(method = "renderTileEntityAt", at = @At("HEAD"), cancellable = true, remap = true)
    private void crosstie$cullFar(TileEntity tileEntity, double x, double y, double z, float partialTicks,
            CallbackInfo ci) {
        if (!CrossTieConfig.machineTesrCullingEnabled || tileEntity == null) {
            return;
        }
        if (!CROSSTIE$TARGET.get(tileEntity.getClass())) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        Entity viewer = mc.renderViewEntity;
        if (viewer == null) {
            return;
        }

        double dx = tileEntity.xCoord + 0.5D - viewer.posX;
        double dy = tileEntity.yCoord + 0.5D - viewer.posY;
        double dz = tileEntity.zCoord + 0.5D - viewer.posZ;
        double distSq = dx * dx + dy * dy + dz * dz;

        // セーフティネット: 13m以内は無条件で描画
        if (distSq <= CROSSTIE$SAFE_RADIUS_SQ) {
            return;
        }

        // 遠距離カリング: (描画距離 + 1チャンク) 超過は描画をスキップ
        if (distSq > crosstie$getMaxDistSq(mc.gameSettings.renderDistanceChunks)) {
            ci.cancel();
        }
    }
}
