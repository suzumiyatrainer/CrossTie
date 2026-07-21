package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.electric.TileEntityElectricalWiring;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TileEntityElectricalWiring の updateEntity を間引き処理する。
 *
 * <p>通常の配線済み状態（isActivated = false）では super.updateEntity() のみが呼ばれており、
 * これは TileEntity の空処理と等価である。これを毎tick呼び出すのは無駄であるため、
 * 20tick に 1 回のみ実行に絞ることでクライアントFPS を改善する。
 */
@Mixin(value = TileEntityElectricalWiring.class, remap = false)
public abstract class TileEntityEWThrottleMixin {

    @Unique
    private int crosstie$ewTick = 0;

    @Inject(method = "updateEntity", at = @At("HEAD"), cancellable = true, remap = false)
    private void crosstie$throttleUpdate(CallbackInfo ci) {
        TileEntityElectricalWiring self = (TileEntityElectricalWiring) (Object) this;
        
        // サーバー側については Connector 等が super.updateEntity() を呼ぶ可能性があるため
        // クライアント側（パーティクル生成処理）のみ間引く
        if (self.getWorldObj() != null && self.getWorldObj().isRemote) {
            if (self.isActivated) {
                // パーティクル生成状態の場合は 20tick に 1 回に間引く
                if (crosstie$ewTick++ % 20 != 0) {
                    ci.cancel();
                }
            } else {
                // 非アクティブな通常状態のクライアント側は完全スキップ
                ci.cancel();
            }
        }
    }
}
