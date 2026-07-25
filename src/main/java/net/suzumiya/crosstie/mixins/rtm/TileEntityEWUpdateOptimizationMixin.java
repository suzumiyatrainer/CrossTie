package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.electric.TileEntityElectricalWiring;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TileEntityElectricalWiring.updateEntity() のクライアント側パーティクル処理を最適化する。
 *
 * <h3>ターゲット実装の構造（KaizPatchX 1.10.1）</h3>
 * <pre>
 * public void updateEntity() {
 *     super.updateEntity();                        // ← TileEntity 標準処理（必須）
 *     if (this.worldObj.isRemote &amp;&amp; this.isActivated) {
 *         // パーティクル生成（クライアント＋アクティブ時のみ）
 *     }
 * }
 * </pre>
 *
 * <h3>このMixinがキャンセルする条件</h3>
 * <ul>
 *   <li>クライアント側かつ非アクティブ状態：パーティクル処理が走らないため全キャンセル可</li>
 * </ul>
 *
 * <h3>キャンセルしない条件</h3>
 * <ul>
 *   <li>サーバー側：{@code super.updateEntity()} がTileEntityの標準ライフサイクルを担う。
 *       ci.cancel() で {@code super.updateEntity()} も省略されるため、サーバー側では必ず通過させる。</li>
 *   <li>クライアント＋アクティブ状態：パーティクル生成処理が必要。
 *       ただし {@code TileEntityEWThrottleMixin} が 20tick に1回に間引くため、
 *       実際のパーティクル生成頻度は削減される。</li>
 * </ul>
 */
@Mixin(value = TileEntityElectricalWiring.class, remap = false)
public abstract class TileEntityEWUpdateOptimizationMixin {

    @Inject(method = "updateEntity", at = @At("HEAD"), cancellable = true)
    private void crosstie$onUpdateEntity(CallbackInfo ci) {
        TileEntityElectricalWiring self = (TileEntityElectricalWiring) (Object) this;
        if (self.getWorldObj() == null) {
            return;
        }
        // サーバー側は super.updateEntity() を通すためキャンセルしない
        if (!self.getWorldObj().isRemote) {
            return;
        }
        // クライアント側: 非アクティブ状態はパーティクル生成なし → 全キャンセル可
        // アクティブ状態は TileEntityEWThrottleMixin に間引きを委ねる
        if (!self.isActivated) {
            ci.cancel();
        }
    }
}
