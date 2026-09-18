package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.entity.train.util.Formation;
import jp.ngt.rtm.entity.train.util.FormationManager;
import net.suzumiya.crosstie.utils.concurrent.CrossTieCasMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link FormationManager} の内部マップをスレッドセーフな {@link CrossTieCasMap} に差し替える。
 *
 * <p>問題: {@code formationMap} は {@link HashMap} のため、将来の並列 Formation 処理や
 * 他 Mod からの非同期アクセスで {@code ConcurrentModificationException} のリスクがある。
 *
 * <p>修正: CrossTie 内包の {@link CrossTieCasMap} (読み取りロックゼロ・CAS 書き込み) に差し替える。
 * GTNHLib・Angelica の有無を問わず、サーバー・クライアント双方で常時適用される。
 */
@Mixin(value = FormationManager.class, remap = false)
public abstract class FormationManagerConcurrentMapMixin {

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "NEW",
                    target = "java/util/HashMap"
            ),
            require = 1,
            remap = false
    )
    private Map<Long, Formation> crosstie$useCasMap() {
        return new CrossTieCasMap<>();
    }
}
