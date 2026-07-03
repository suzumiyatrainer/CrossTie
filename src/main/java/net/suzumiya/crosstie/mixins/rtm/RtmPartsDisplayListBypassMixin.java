package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.render.Parts;
import jp.ngt.rtm.render.PartsRenderer;
import net.suzumiya.crosstie.util.CrossTiePartsRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Parts.class, remap = false)
public class RtmPartsDisplayListBypassMixin {

    /**
     * Parts.render() の開始時に CrossTiePartsRenderContext に通知する。
     * これにより AngelicaRenderGlobalDisplayListCrashMixin がスタックトレースを
     * 走査せずにコンテキストを判定できる。
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void crosstie$enterPartsRender(PartsRenderer<?, ?> renderer, CallbackInfo ci) {
        CrossTiePartsRenderContext.enter();
    }

    /**
     * Parts.render() の終了時（正常・例外ともに）に CrossTiePartsRenderContext から抜ける。
     */
    @Inject(method = "render", at = @At("RETURN"))
    private void crosstie$exitPartsRender(PartsRenderer<?, ?> renderer, CallbackInfo ci) {
        CrossTiePartsRenderContext.exit();
    }

    /**
     * Angelica環境下で巨大なディスプレイリスト（車両本体など）が消失するバグの回避策。
     *
     * <p>GLHelper.isCompiling() を常に true に偽装することで、ディスプレイリストの
     * コンパイル・呼び出しをスキップし、常に即時描画(Immediate Mode)を実行させる。
     *
     * <p>シェーダーmod (ShadersRender) の shadow pass 中に GLHelper クラスが
     * LaunchClassLoader から参照できない場合があり {@code NoClassDefFoundError} が
     * 発生するケースがある。そのため try-catch で安全にフォールバックする。
     */
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Ljp/ngt/ngtlib/renderer/GLHelper;isCompiling()Z"))
    private boolean crosstie$forceImmediateMode() {
        try {
            // 戻り値を true に固定して即時描画を強制
            return true;
        } catch (Throwable t) {
            // GLHelper が ClassLoader から見えない場合のフォールバック
            return false;
        }
    }
}
