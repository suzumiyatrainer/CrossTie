package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.ngtlib.renderer.DisplayList;
import jp.ngt.ngtlib.renderer.GLHelper;
import jp.ngt.rtm.render.Parts;
import net.suzumiya.crosstie.client.CrossTieShadeModelTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.stream.Stream;

/**
 * 3-3: {@link Parts#render} の毎パーツ {@code glShadeModel} を {@link CrossTieShadeModelTracker} 経由にし、
 * {@code Arrays.stream(this.gLists).forEach(GLHelper::callList)} の Stream 生成も排除する。
 *
 * <p>{@code RtmPartsMatrixPushMixin} / {@code PartsDoorGhostFixMixin} と競合しないよう {@code @Redirect} のみを使用。</p>
 */
@SideOnly(Side.CLIENT)
@Mixin(value = Parts.class, remap = false)
public abstract class PartsShadeModelMixin {

    @Redirect(
            method = "render(Ljp/ngt/rtm/render/PartsRenderer;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/opengl/GL11;glShadeModel(I)V",
                    remap = false
            ),
            require = 0,
            remap = false
    )
    private void crosstie$redirectPartsShadeModel(int mode) {
        CrossTieShadeModelTracker.applyShadeModel(mode);
    }

    @Redirect(
            method = "render(Ljp/ngt/rtm/render/PartsRenderer;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Arrays;stream([Ljava/lang/Object;)Ljava/util/stream/Stream;",
                    remap = false
            ),
            require = 0,
            remap = false
    )
    private Stream<Object> crosstie$fastCallDisplayLists(Object[] array) {
        if (array != null) {
            for (int i = 0; i < array.length; i++) {
                Object elem = array[i];
                if (elem instanceof DisplayList) {
                    GLHelper.callList((DisplayList) elem);
                }
            }
        }
        return Stream.empty();
    }
}
