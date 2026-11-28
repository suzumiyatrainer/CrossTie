package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.ngtlib.block.TileEntityCustom;
import jp.ngt.rtm.rail.TileEntityLargeRailBase;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 2-1 & 2-5: {@link TileEntityLargeRailBase} のダミーブロック毎 tick 空回しを廃止し、
 * {@code getBlockHeights} を OptiFine Multi-Core / Angelica 両環境でスレッドセーフな
 * ロックフリー不変キャッシュ化する Mixin。
 *
 * <ul>
 *   <li>ダミーブロック ({@code TileEntityLargeRailBase} / {@code SlopeBase} / {@code SwitchBase} /
 *       {@code TileEntityTurnplateParts}) のみ {@code canUpdate() = false} にし、
 *       {@code TileEntityLargeRailCore} サブクラスはすべて {@code true} を維持する（WebCTC 互換保証）。</li>
 *   <li>{@code defaultHeight = 0.0625F}（当たり判定用）と任意の {@code prop.blockHeight}（道床描画用）を
 *       {@code volatile} 不変オブジェクトの Safe Publication でキャッシュする。</li>
 * </ul>
 */
@Mixin(value = TileEntityLargeRailBase.class, remap = false)
public abstract class TileEntityLargeRailBaseOptimizationMixin extends TileEntityCustom {

    @Shadow
    private boolean finishSetupBlockBounds;

    @Shadow
    private float[] blockHeights;

    @Shadow
    public abstract TileEntityLargeRailCore getRailCore();

    @Shadow
    protected abstract float[] getBlockHeights(int x, int y, int z, float defaultHeight);

    @Unique
    private static final float[] CROSSTIE_DEFAULT_HEIGHTS = new float[] { 0.0625F, 0.0625F, 0.0625F, 0.0625F };

    /** defaultHeight == 0.0625F 基準の不変配列キャッシュ（volatile Safe Publication） */
    @Unique
    private volatile float[] crosstie$baseHeights;

    /** defaultHeight != 0.0625F（RenderBlockLargeRail 用）の不変ホルダキャッシュ */
    @Unique
    private volatile CrosstieCachedHeights crosstie$customHeights;

    @Unique
    private static final class CrosstieCachedHeights {
        final float defaultHeight;
        final float[] heights;

        CrosstieCachedHeights(float defaultHeight, float[] heights) {
            this.defaultHeight = defaultHeight;
            this.heights = heights;
        }
    }

    /**
     * Minecraft Forge 追加メソッド {@code TileEntity.canUpdate()}（SRG 名なし）。
     * ダミーブロックは tick 不要、Core 系のみ tick 有効を維持する。
     */
    @Override
    public boolean canUpdate() {
        return ((Object) this) instanceof TileEntityLargeRailCore;
    }

    @Inject(method = "setStartPoint", at = @At("TAIL"), remap = false)
    private void crosstie$invalidateHeightsOnSetStartPoint(int x, int y, int z, CallbackInfo ci) {
        this.crosstie$baseHeights = null;
        this.crosstie$customHeights = null;
        this.blockHeights = null;
    }

    @Inject(method = "onDataPacket", at = @At("TAIL"), remap = true)
    private void crosstie$onDataPacketTail(NetworkManager net, S35PacketUpdateTileEntity pkt, CallbackInfo ci) {
        this.crosstie$baseHeights = null;
        this.crosstie$customHeights = null;
        this.blockHeights = null;
        if (this.worldObj != null && this.worldObj.isRemote && !this.finishSetupBlockBounds && this.getRailCore() != null) {
            this.finishSetupBlockBounds = true;
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        }
    }

    /**
     * @author CrossTie
     * @reason 1) canUpdate()=false 化に伴い finishSetupBlockBounds ゲートを遅延計算に置換。
     *         2) OptiFine Multi-Core (WrUpdateThread) からの並行アクセスでも安全な volatile 不変キャッシュを導入。
     */
    @Overwrite
    public float[] getBlockHeights(int x, int y, int z, float defaultHeight, boolean useCache) {
        if (useCache) {
            if (defaultHeight == 0.0625F) {
                float[] base = this.crosstie$baseHeights;
                if (base != null) {
                    return base;
                }
            } else {
                CrosstieCachedHeights custom = this.crosstie$customHeights;
                if (custom != null && custom.defaultHeight == defaultHeight) {
                    return custom.heights;
                }
                float[] base = this.crosstie$baseHeights;
                if (base != null) {
                    float delta = defaultHeight - 0.0625F;
                    float[] derived = new float[] {
                            base[0] + delta, base[1] + delta, base[2] + delta, base[3] + delta
                    };
                    this.crosstie$customHeights = new CrosstieCachedHeights(defaultHeight, derived);
                    return derived;
                }
            }
        }

        // 基準値 (0.0625F) で計算すれば任意 defaultHeight へ O(1) で派生可能
        float[] computedBase = this.getBlockHeights(x, y, z, 0.0625F);
        if (computedBase != null) {
            if (useCache) {
                this.crosstie$baseHeights = computedBase;
                this.blockHeights = computedBase;
                this.finishSetupBlockBounds = true;
            }
            if (defaultHeight == 0.0625F) {
                return computedBase;
            }
            float delta = defaultHeight - 0.0625F;
            float[] derived = new float[] {
                    computedBase[0] + delta, computedBase[1] + delta, computedBase[2] + delta, computedBase[3] + delta
            };
            if (useCache) {
                this.crosstie$customHeights = new CrosstieCachedHeights(defaultHeight, derived);
            }
            return derived;
        }

        return CROSSTIE_DEFAULT_HEIGHTS;
    }
}
