package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.render.Parts;
import jp.ngt.rtm.render.PartsRenderer;
import net.suzumiya.crosstie.utils.CrossTiePartsRenderContext;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code Parts.render()} に対するフック:
 * <ul>
 * <li>HEAD: {@code GL11.glPushMatrix()} +
 * {@link CrossTiePartsRenderContext#enter()}</li>
 * <li>RETURN: {@code GL11.glPopMatrix()} +
 * {@link CrossTiePartsRenderContext#exit()}</li>
 * </ul>
 *
 * <p>
 * 【注意】旧 {@code RtmPartsDisplayListBypassMixin} にあった
 * {@code GLHelper.isCompiling()} の {@code @Redirect} はここには含まない。元のバイパスは
 * {@code CrossTieMixinPlugin} で {@code isModPresent("AngelicaGlsm")}
 * を条件としていたが、実際の Angelica の mod ID は {@code "angelica"} であるため、このチェックは常に false
 * となっており、 バイパスは一度も適用されたことがなかった。<br>
 * 実行時の {@code Class.forName} 方式に変えると Angelica が検出されて全パーツが Immediate Mode
 * 描画に強制され、FPS が 165 → 20 以下まで崩壊したため削除した。
 */
@Mixin(value = Parts.class, remap = false)
public class RtmPartsMatrixPushMixin {

    /**
     * Parts.render() の開始時: glPushMatrix() を実行し、コンテキストに通知する。
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void crosstie$onRenderHead(PartsRenderer<?, ?> renderer, CallbackInfo ci) {
        GL11.glPushMatrix();
        CrossTiePartsRenderContext.enter();
    }

    /**
     * Parts.render() の終了時: glPopMatrix() を実行し、コンテキストから抜ける。
     */
    @Inject(method = "render", at = @At("RETURN"))
    private void crosstie$onRenderReturn(PartsRenderer<?, ?> renderer, CallbackInfo ci) {
        GL11.glPopMatrix();
        CrossTiePartsRenderContext.exit();
    }
}
