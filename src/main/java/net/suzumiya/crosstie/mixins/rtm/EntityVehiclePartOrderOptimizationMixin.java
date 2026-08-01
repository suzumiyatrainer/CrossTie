package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.train.parts.EntityVehiclePart;
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * EntityVehiclePart.checkEntityOrder() の O(N)×3 loadedEntityList 操作を最適化する。
 *
 * <h3>問題</h3>
 * <p>
 * オリジナルの {@code checkEntityOrder()} は {@code sorted == false} の間、毎tick
 * {@code loadedEntityList.indexOf(this)} と {@code indexOf(vehicle)} を各 O(N)
 * で呼び出し、 さらに {@code remove/add} も O(N) となる（合計 O(N)×3）。
 * </p>
 *
 * <h3>対策</h3>
 * <p>
 * {@code sorted} フィールドに早期に {@code true} をセットすることで、 クライアント側の毎tick スキャンを抑制する。
 * EntityVehiclePart はチャンクロード直後に一度だけ順序調整すれば十分であり、 それ以降の毎tick チェックは無駄なコストとなっている。
 * </p>
 *
 * <h3>実装注記</h3>
 * <p>
 * {@code checkEntityOrder()} は private メソッドのため {@code @Inject} ではなく
 * {@code onUpdate} への {@code @Inject} で {@code sorted} を早期に立てる。 VehiclePart が
 * Vehicle に紐付いた時点で順序は確定とみなし、以降のスキャンをスキップする。
 * </p>
 */
@SuppressWarnings("rawtypes")
@Mixin(value = EntityVehiclePart.class, remap = false)
public abstract class EntityVehiclePartOrderOptimizationMixin {

    @Shadow
    private boolean sorted;

    @Shadow
    public abstract EntityVehicleBase getVehicle();

    /**
     * onUpdate の HEAD で Vehicle が既に設定されていれば sorted=true を強制設定する。 これにより
     * checkEntityOrder 内の O(N)×3 処理が初回以降スキップされる。
     */
    @Inject(method = "onUpdate", at = @At("HEAD"), require = 0, remap = false)
    private void crosstie(CallbackInfo ci) {
        if (!this.sorted && this.getVehicle() != null) {
            // Vehicle が既に設定されている場合、Entity 順序の再整列は不要。
            // Vanilla の描画ループは loadedEntityList 順序に依存しないため
            // 順序を変えなくても描画上の問題は発生しない。
            this.sorted = true;
        }
    }
}
