package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import jp.ngt.rtm.rail.util.RailProperty;
import net.suzumiya.crosstie.CrossTie;
import net.suzumiya.crosstie.util.Hi03ExpressRailwayContext;
import net.suzumiya.crosstie.util.ModDetector;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RailPartsRenderer optimization and compatibility mixin.
 * 
 * 機能:
 * 1. 距離ベースのカリング最適化
 * 2. hi03ExpressRailway Angelica互換性修正（Angelica存在時のみ有効）
 */
@Mixin(targets = "jp.ngt.rtm.render.RailPartsRenderer", remap = false)
public abstract class RTMRailPartsRenderMixin {

    /**
     * hi03ExpressRailwayモデル検出とコンテキスト有効化
     * Angelicaがロードされている場合のみ有効
     * renderRail開始時にモデル名をチェック
     * 
     * 最適化: 毎フレームのString.containsを防ぐため、結果をTileEntityにキャッシュ
     */
    @Inject(method = "renderRail", at = @At("HEAD"), remap = false)
    private void crosstie$enterHi03Context(TileEntityLargeRailCore tileEntity, int index, double x, double y, double z,
            float par8, CallbackInfo ci) {
        // Angelicaがない場合は何もしない（修正不要）
        if (!ModDetector.needsAngelicaHi03Fix()) {
            return;
        }

        // キャッシュチェック (Duck Typing)
        if (tileEntity instanceof ICrossTieRail) {
            byte cache = ((ICrossTieRail) tileEntity).crosstie$getHi03Cache();
            if (cache == 1) {
                // キャッシュ: hi03である
                Hi03ExpressRailwayContext.enter();
                return;
            } else if (cache == 2) {
                // キャッシュ: hi03ではない
                return;
            }
            // cache == 0 (未判定) -> 下の判定へ進む
        }

        try {
            // TileEntityからRailPropertyを取得し、railModelフィールドを直接確認
            RailProperty property = tileEntity.getProperty();
            if (property != null && property.railModel != null) {
                // railModelは"hi03ExpressRailway_RailA_Ballast"のような形式
                boolean isHi03 = property.railModel.contains("hi03ExpressRailway");

                // キャッシュ更新
                if (tileEntity instanceof ICrossTieRail) {
                    ((ICrossTieRail) tileEntity).crosstie$setHi03Cache((byte) (isHi03 ? 1 : 2));
                }

                if (isHi03) {
                    Hi03ExpressRailwayContext.enter();
                }
            }
        } catch (Exception e) {
            // モデル名取得失敗時は無視(安全にフォールバック)
        }
    }

    /**
     * renderRail終了時にコンテキストを確実に終了
     */
    @Inject(method = "renderRail", at = @At("RETURN"), remap = false)
    private void crosstie$exitHi03Context(TileEntityLargeRailCore tileEntity, int index, double x, double y, double z,
            float par8, CallbackInfo ci) {
        Hi03ExpressRailwayContext.exit();
    }

    /**
     * 距離ベースのカリング最適化
     * プレイヤーから遠すぎるレールのレンダリングをスキップ
     */
    @Inject(method = "renderRail", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$cullRailParts(TileEntityLargeRailCore tileEntity, int index, double x, double y, double z,
            float par8, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderViewEntity == null)
            return;

        int renderChunks = CrossTie.proxy.getClientRenderDistance();
        if (renderChunks < 4)
            renderChunks = 4;

        double cullDist = renderChunks * 16.0;

        // TileEntity.getDistanceFrom returns squared distance
        if (tileEntity.getDistanceFrom(mc.renderViewEntity.posX, mc.renderViewEntity.posY,
                mc.renderViewEntity.posZ) > cullDist * cullDist) {
            ci.cancel();
        }
    }
}
