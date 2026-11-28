package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.rtm.render.PartsRenderer;
import net.suzumiya.crosstie.client.CrossTieShadeModelTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 3-3: {@link PartsRenderer#renderLightEffect} が {@code ModelObject.render()} の途中で
 * {@code GL_FLAT} に戻してしまうエッジケースを {@link CrossTieShadeModelTracker} で安全に吸収する。
 */
@SideOnly(Side.CLIENT)
@Mixin(value = PartsRenderer.class, remap = false)
public abstract class PartsRendererShadeModelMixin {

    @Redirect(
            method = "renderLightEffect(Lorg/lwjgl/util/vector/Vector3f;[DFFFIIZ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/opengl/GL11;glShadeModel(I)V",
                    remap = false
            ),
            require = 0,
            remap = false
    )
    private void crosstie$redirectLightEffectShadeModel(int mode) {
        CrossTieShadeModelTracker.applyShadeModel(mode);
    }
}
