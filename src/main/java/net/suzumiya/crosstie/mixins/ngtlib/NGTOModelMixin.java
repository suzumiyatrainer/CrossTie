package net.suzumiya.crosstie.mixins.ngtlib;

import net.suzumiya.crosstie.util.Hi03ExpressRailwayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NGTOModel 描画中は Angelica の display-list 変換を回避するため、
 * 既存の Legacy display-list コンテキストを有効化する。
 */
@Mixin(targets = "jp.ngt.ngtlib.renderer.model.NGTOModel", remap = false)
public class NGTOModelMixin {

    @Inject(method = "renderAll", at = @At("HEAD"), remap = false)
    private void crosstie$enterLegacyDisplayListContext(boolean smoothing, CallbackInfo ci) {
        Hi03ExpressRailwayContext.enter();
    }

    @Inject(method = "renderAll", at = @At("RETURN"), remap = false)
    private void crosstie$exitLegacyDisplayListContext(boolean smoothing, CallbackInfo ci) {
        Hi03ExpressRailwayContext.exit();
    }
}
