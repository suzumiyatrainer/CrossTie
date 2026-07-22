package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.electric.TileEntityInsulator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TileEntityInsulator（碍子）には信号の入出力・転送ロジックが一切存在しないため、
 * 毎Tick呼び出される updateEntity() 処理をキャンセルして Tick 負荷（MSPT）を完全にゼロ化します。
 */
@Mixin(value = TileEntityInsulator.class, remap = false)
public abstract class TileEntityInsulatorOptimizationMixin {

    @Inject(method = "updateEntity", at = @At("HEAD"), cancellable = true)
    private void onUpdateEntity(CallbackInfo ci) {
        // 碍子のTick更新処理をスキップ
        ci.cancel();
    }
}
