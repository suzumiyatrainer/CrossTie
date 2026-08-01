package net.suzumiya.crosstie.mixins.rtm;

import net.suzumiya.crosstie.CrossTieConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.stream.IntStream;

/**
 * {@code EntityTrainDetector} の在線確認処理（{@code onUpdate} 内の IntStream スキャン）を
 * スロットリングし、サーバーの MSPT を低減する。
 *
 * <h3>安全性について</h3>
 * {@code EntityTrainDetector} の {@code onUpdate()} は毎tick、レール下8マスまでのTileEntityを
 * 走査して列車在線を判定する。最高速 320km/h ≒ 88.9m/s の列車でも、
 * デフォルト間隔4tickでの移動距離は約 1.4m（= 1レールピッチ未満）であり、
 * 確実に検知できる。
 *
 * <h3>動作</h3>
 * {@code @Redirect} を用いて {@code IntStream.range(0, 8)} の実行タイミングを制御する。
 * スロットリング対象のtickでは {@code IntStream.empty()} を返すことで在線探査をスキップし、
 * 前回結果（{@code findTrain}）を安全に維持する。
 */
@Mixin(targets = "jp.ngt.rtm.entity.EntityTrainDetector", remap = false)
public abstract class EntityTrainDetectorThrottleMixin {

    /** スロットリング用フレームカウンター */
    @Unique
    private int crosstie$tickCount = 0;

    /**
     * {@code EntityTrainDetector.onUpdate()} 内の {@code IntStream.range(0, 8)} をフックし、
     * スロットリング対象tickでは空の Stream を返して探査を回避する。
     */
    @Redirect(
            method = "onUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/stream/IntStream;range(II)Ljava/util/stream/IntStream;",
                    remap = false
            ),
            require = 0,
            remap = false
    )
    private IntStream crosstie$throttleRange(int start, int end) {
        if (!CrossTieConfig.detectorThrottlingEnabled) {
            return IntStream.range(start, end);
        }

        int interval = CrossTieConfig.detectorThrottleInterval;
        if (interval <= 1) {
            return IntStream.range(start, end);
        }

        if (++crosstie$tickCount % interval != 0) {
            // スロットリング対象tick: 空のIntStreamを返すことで TileEntity 探査ループを完全にスキップ
            return IntStream.empty();
        }

        return IntStream.range(start, end);
    }
}
