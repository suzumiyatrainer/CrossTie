package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.suzumiya.crosstie.CrossTieConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 車止め（{@code EntityBumpingPost}）の描画カリング。
 *
 * <p>
 * {@code RenderEntityInstalledObject.doRender()} をフックし、以下の2条件いずれかで描画をスキップする:
 * <ol>
 * <li>エンティティとプレイヤーの距離がバニラの描画距離（Render Distance チャンク × 16m）以上</li>
 * <li>エンティティがフラストラム（視錐台）の外側にいる</li>
 * </ol>
 *
 * <p>
 * 対象は {@code EntityBumpingPost} のみ。{@code EntityTrainDetector} や
 * {@code EntityATC} は 自身で {@code ignoreFrustumCheck = true}
 * を設定しており、バニラ側でフラストラムチェックが スキップされているため、このMixinでの対処は不要。信号機・踏切については
 * {@code TileEntitySignalNoCullingMixin} /
 * {@code TileEntityCrossingGateNoCullingMixin} で既に対応済み。
 */
@Mixin(targets = "jp.ngt.rtm.entity.RenderEntityInstalledObject", remap = false)
public abstract class RenderEntityInstalledObjectCullingMixin {

    @Inject(method = "doRender", at = @At("HEAD"), cancellable = true, remap = true)
    private void crosstie$cullBumpingPost(Entity entity, double par2, double par4, double par6, float par8, float par9,
            CallbackInfo ci) {

        // 車止め（EntityBumpingPost）のみに適用する
        if (!"jp.ngt.rtm.entity.EntityBumpingPost".equals(entity.getClass().getName())) {
            return;
        }

        // Config で無効化されている場合はスキップ
        if (!CrossTieConfig.installedObjectCullingEnabled) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        Entity renderView = mc.renderViewEntity;
        if (renderView == null) {
            return;
        }

        // === 1. 描画距離カリング ===
        // バニラの renderDistanceChunks を使用して描画距離の二乗を計算する
        int renderChunks = mc.gameSettings.renderDistanceChunks;
        double renderDist = renderChunks * 16.0D;
        double renderDistSq = renderDist * renderDist;

        double dx = entity.posX - renderView.posX;
        double dy = entity.posY - renderView.posY;
        double dz = entity.posZ - renderView.posZ;
        double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq >= renderDistSq) {
            ci.cancel();
            return;
        }

        // === 2. フラストラムカリング ===
        // 注: フラストラムキャッシュで使用している ICamera#setPosition が内部で glGetFloat を
        // 呼び出し、シェーダー環境で極度のFPS低下を引き起こすため削除。
        // （バニラのエンティティカリングと上記の距離カリングに依存する）
    }
}
