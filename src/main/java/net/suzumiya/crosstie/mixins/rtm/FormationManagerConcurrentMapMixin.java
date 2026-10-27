package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.train.util.Formation;
import jp.ngt.rtm.entity.train.util.FormationManager;
import net.suzumiya.crosstie.utils.concurrent.CrossTieCasMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link FormationManager} の内部マップをスレッドセーフな {@link CrossTieCasMap} に差し替える。
 *
 * <p>
 * 問題: {@code formationMap} は {@link HashMap} のため、将来の並列 Formation 処理や 他 Mod
 * からの非同期アクセスで {@code ConcurrentModificationException} のリスクがある。
 *
 * <p>
 * 修正: CrossTie 内包の {@link CrossTieCasMap} (読み取りロックゼロ・CAS 書き込み) に差し替える。
 * GTNHLib・Angelica の有無を問わず、サーバー・クライアント双方で常時適用される。
 */
@Mixin(value = FormationManager.class, remap = false)
public abstract class FormationManagerConcurrentMapMixin {

    @Unique
    private static final AtomicLong crosstie$lastId = new AtomicLong();

    @Shadow
    public abstract Formation getFormation(long id);

    @Redirect(method = "<init>", at = @At(value = "NEW", target = "java/util/HashMap"), require = 1, remap = false)
    private HashMap<Long, Formation> crosstie$useCasMap() {
        return new CrossTieCasMap<>();
    }

    /**
     * 元実装は System.currentTimeMillis() のため、同一ミリ秒に複数の編成を作ると ID が衝突し、
     * 編成マップ上で互いを上書きして中間車両が編成から欠落する。単調増加かつ未使用の ID を返す。
     */
    @Inject(method = "getNewFormationId", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void crosstie$uniqueFormationId(CallbackInfoReturnable<Long> cir) {
        long now = System.currentTimeMillis();
        long prev, next;
        do {
            prev = crosstie$lastId.get();
            next = Math.max(now, prev + 1);
        } while (!crosstie$lastId.compareAndSet(prev, next));
        while (this.getFormation(next) != null) {
            next = crosstie$lastId.incrementAndGet();
        }
        cir.setReturnValue(next);
    }
}
