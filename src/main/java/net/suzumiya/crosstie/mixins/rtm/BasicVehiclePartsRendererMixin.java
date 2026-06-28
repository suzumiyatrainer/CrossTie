package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.render.BasicVehiclePartsRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.List;

@Mixin(value = BasicVehiclePartsRenderer.class, remap = false)
public class BasicVehiclePartsRendererMixin {

    @Redirect(method = "init", at = @At(value = "INVOKE", target = "Ljava/util/List;toArray([Ljava/lang/Object;)[Ljava/lang/Object;"))
    private Object[] crosstie$fixBodyPartsArray(List<String> bodyParts, Object[] a) {
        // RTMオリジナルのバグ: bodyParts.toArray(new String[list.size()]) のように、
        // 無関係な list.size() で配列を初期化しているため、配列サイズが大きくなり null が混入して
        // NPE で車体描画が消えるバグを new String[0] で正しく初期化するように修正。
        return bodyParts.toArray(new String[0]);
    }
}
