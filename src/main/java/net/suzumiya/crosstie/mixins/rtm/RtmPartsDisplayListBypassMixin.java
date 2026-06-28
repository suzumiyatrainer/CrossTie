package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.render.Parts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = Parts.class, remap = false)
public class RtmPartsDisplayListBypassMixin {

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Ljp/ngt/ngtlib/renderer/GLHelper;isCompiling()Z"))
    private boolean crosstie$forceImmediateMode() {
        // Angelica環境下で巨大なディスプレイリスト（車両本体など）が消失するバグの回避策。
        // isCompiling() を常に true に偽装することで、ディスプレイリストのコンパイル・呼び出しをスキップし、
        // 常に即時描画(Immediate Mode)を実行させる。
        return true;
    }
}
